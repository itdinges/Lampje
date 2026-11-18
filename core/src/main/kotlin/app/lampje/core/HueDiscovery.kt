package app.lampje.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

data class DiscoveredBridge(val id: String, val ip: String)

object HueDiscovery {
    /** Asks Signify's discovery endpoint which bridges share this network's public IP. */
    suspend fun viaCloud(client: OkHttpClient = OkHttpClient()): List<DiscoveredBridge> =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url("https://discovery.meethue.com").build()
            val text = client.newCall(req).execute().use { it.body?.string().orEmpty() }
            HueJson.parseToJsonElement(text).jsonArray.mapNotNull {
                val obj = it.jsonObject
                val ip = obj["internalipaddress"]?.jsonPrimitive?.content ?: return@mapNotNull null
                DiscoveredBridge(obj["id"]?.jsonPrimitive?.content.orEmpty(), ip)
            }
        }
}
