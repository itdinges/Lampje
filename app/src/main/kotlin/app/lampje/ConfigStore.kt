package app.lampje

import android.content.Context
import app.lampje.core.BridgeConfig

/** Stores the bridge address, application key and pinned certificate in app-private storage. */
class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("bridge", Context.MODE_PRIVATE)

    fun load(): BridgeConfig? {
        val ip = prefs.getString("ip", null) ?: return null
        val key = prefs.getString("key", null) ?: return null
        val cert = prefs.getString("cert", null) ?: return null
        return BridgeConfig(ip, key, cert)
    }

    fun save(config: BridgeConfig) {
        prefs.edit()
            .putString("ip", config.ip)
            .putString("key", config.appKey)
            .putString("cert", config.certSha256)
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()
}
