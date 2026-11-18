package app.lampje

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lampje.core.BridgeConfig
import app.lampje.core.DiscoveredBridge
import app.lampje.core.HueConnect
import app.lampje.core.HueDiscovery
import app.lampje.core.HueSnapshot
import app.lampje.core.HueStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PairState {
    data object Idle : PairState
    data object Busy : PairState
    data class Failed(val message: String) : PairState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val configStore = ConfigStore(app)

    private val _config = MutableStateFlow(configStore.load())
    val config: StateFlow<BridgeConfig?> = _config.asStateFlow()

    private val _snapshot = MutableStateFlow(HueSnapshot())
    val snapshot: StateFlow<HueSnapshot> = _snapshot.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _pairState = MutableStateFlow<PairState>(PairState.Idle)
    val pairState: StateFlow<PairState> = _pairState.asStateFlow()

    private val _discovered = MutableStateFlow<List<DiscoveredBridge>>(emptyList())
    val discovered: StateFlow<List<DiscoveredBridge>> = _discovered.asStateFlow()

    private var store: HueStore? = null
    private var session: Job? = null

    init {
        _config.value?.let(::connect)
    }

    private fun connect(config: BridgeConfig) {
        session?.cancel()
        val newStore = HueStore(HueConnect.api(config))
        store = newStore
        session = viewModelScope.launch {
            launch { newStore.snapshot.collect { _snapshot.value = it } }
            launch { newStore.error.collect { _connectionError.value = it?.let { e -> e.message ?: e.javaClass.simpleName } } }
            newStore.follow()
        }
    }

    fun pair(ip: String) {
        viewModelScope.launch {
            _pairState.value = PairState.Busy
            try {
                val config = HueConnect.pair(ip.trim())
                configStore.save(config)
                _config.value = config
                connect(config)
                _pairState.value = PairState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _pairState.value = PairState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun discover() {
        viewModelScope.launch {
            try {
                _discovered.value = HueDiscovery.viaCloud()
                if (_discovered.value.isEmpty()) _pairState.value = PairState.Failed("No bridge found")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _pairState.value = PairState.Failed(e.message ?: "Discovery failed")
            }
        }
    }

    fun disconnect() {
        session?.cancel()
        store = null
        configStore.clear()
        _config.value = null
        _snapshot.value = HueSnapshot()
        _connectionError.value = null
        _pairState.value = PairState.Idle
    }

    /** Runs a bridge write in the background and reports failures as a message. */
    fun act(block: suspend HueStore.() -> Unit) {
        val current = store ?: return
        viewModelScope.launch {
            try {
                current.block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = e.message ?: e.javaClass.simpleName
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
