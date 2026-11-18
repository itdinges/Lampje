package app.lampje

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LampjeApp(vm: MainViewModel) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors) {
        val config by vm.config.collectAsStateWithLifecycle()
        if (config == null) SetupScreen(vm) else MainScreen(vm)
    }
}

@Composable
private fun SetupScreen(vm: MainViewModel) {
    val pairState by vm.pairState.collectAsStateWithLifecycle()
    val discovered by vm.discovered.collectAsStateWithLifecycle()
    var ip by rememberSaveable { mutableStateOf("") }

    Scaffold { padding ->
        Column(
            Modifier.padding(padding).padding(24.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Connect to your Hue Bridge", style = MaterialTheme.typography.headlineSmall)
            Text("Enter the bridge IP address, press the round link button on the bridge, then tap Pair.")
            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text("Bridge IP address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = vm::discover) { Text("Find bridge on my network") }
            discovered.forEach {
                TextButton(onClick = { ip = it.ip }) { Text("${it.ip}  (${it.id})") }
            }
            Button(
                onClick = { vm.pair(ip) },
                enabled = ip.isNotBlank() && pairState != PairState.Busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (pairState == PairState.Busy) "Pairing..." else "Pair")
            }
            (pairState as? PairState.Failed)?.let {
                Text(it.message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Lights("Lights", Icons.Default.Lightbulb),
    Scenes("Scenes", Icons.Default.Movie),
    Zones("Zones", Icons.Default.Layers),
    Automations("Automations", Icons.Default.Schedule),
    Motion("Motion", Icons.Default.Sensors),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(vm: MainViewModel) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val connectionError by vm.connectionError.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lampje") },
                actions = {
                    IconButton(onClick = vm::disconnect) { Icon(Icons.Default.LinkOff, contentDescription = "Forget bridge") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { index, entry ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            connectionError?.let {
                Text(
                    "Bridge unreachable: $it",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                )
            }
            Box(Modifier.fillMaxSize()) {
                when (Tab.entries[tab]) {
                    Tab.Lights -> LightsTab(snapshot, vm)
                    Tab.Scenes -> ScenesTab(snapshot, vm)
                    Tab.Zones -> ZonesTab(snapshot, vm)
                    Tab.Automations -> AutomationsTab(snapshot, vm)
                    Tab.Motion -> MotionTab(snapshot, vm)
                }
            }
        }
    }
}
