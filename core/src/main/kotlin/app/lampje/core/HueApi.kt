package app.lampje.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit

open class HueApiException(val status: Int, message: String) : IOException(message)

class LinkButtonNotPressedException : HueApiException(0, "Press the link button on the bridge and try again")

/** One entry of the CLIP v2 event stream. [type] is add, update, delete or error. */
data class HueEvent(val type: String, val data: List<JsonObject>)

/** Thin client for the local Hue CLIP v2 API. Resources are returned as raw JSON. */
class HueApi(
    private val baseUrl: HttpUrl,
    private val appKey: String,
    private val client: OkHttpClient,
) {
    suspend fun list(type: String): List<JsonObject> =
        request("GET", type, null).map { it.jsonObject }

    /** Returns the id of the created resource, if the bridge reports one. */
    suspend fun create(type: String, body: JsonObject): String? =
        request("POST", type, body).firstOrNull()?.jsonObject?.get("rid")?.jsonPrimitive?.content

    suspend fun update(type: String, id: String, body: JsonObject) {
        request("PUT", "$type/$id", body)
    }

    suspend fun delete(type: String, id: String) {
        request("DELETE", "$type/$id", null)
    }

    private suspend fun request(method: String, path: String, body: JsonObject?): JsonArray =
        withContext(Dispatchers.IO) {
            val url = baseUrl.newBuilder().addEncodedPathSegments("clip/v2/resource/$path").build()
            val req = Request.Builder()
                .url(url)
                .header("hue-application-key", appKey)
                .method(method, body?.let { HueJson.encodeToString(JsonObject.serializer(), it).toRequestBody(JSON) })
                .build()
            client.newCall(req).execute().use { parseResource(it) }
        }

    private fun parseResource(response: Response): JsonArray {
        val text = response.body?.string().orEmpty()
        val root = runCatching { HueJson.parseToJsonElement(text).jsonObject }.getOrNull()
        val errors = root?.get("errors")?.jsonArray.orEmpty()
        if (errors.isNotEmpty()) {
            val msg = errors.joinToString("; ") { it.jsonObject["description"]?.jsonPrimitive?.content.orEmpty() }
            throw HueApiException(response.code, msg)
        }
        if (!response.isSuccessful) throw HueApiException(response.code, "HTTP ${response.code}")
        return root?.get("data")?.jsonArray ?: JsonArray(emptyList())
    }

    /** Realtime changes. Completes with an error when the stream drops; callers should reconnect. */
    fun events(): Flow<List<HueEvent>> = callbackFlow {
        val req = Request.Builder()
            .url(baseUrl.newBuilder().addEncodedPathSegments("eventstream/clip/v2").build())
            .header("hue-application-key", appKey)
            .header("Accept", "text/event-stream")
            .build()
        val streamClient = client.newBuilder().readTimeout(0, TimeUnit.SECONDS).build()
        val source = EventSources.createFactory(streamClient).newEventSource(req, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val events = runCatching { parseEvents(data) }.getOrNull() ?: return
                trySend(events)
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(t ?: HueApiException(response?.code ?: 0, "Event stream closed"))
            }
        })
        awaitClose { source.cancel() }
    }

    companion object {
        private val JSON = "application/json".toMediaType()

        fun parseEvents(data: String): List<HueEvent> =
            HueJson.parseToJsonElement(data).jsonArray.map {
                val obj = it.jsonObject
                HueEvent(
                    type = obj["type"]?.jsonPrimitive?.content.orEmpty(),
                    data = obj["data"]?.jsonArray.orEmpty().map { d -> d.jsonObject },
                )
            }

        /** Creates an application key on the bridge. The link button must have been pressed. */
        suspend fun pair(baseUrl: HttpUrl, client: OkHttpClient, deviceType: String): String =
            withContext(Dispatchers.IO) {
                val body = buildJsonObject {
                    put("devicetype", deviceType)
                    put("generateclientkey", true)
                }
                val req = Request.Builder()
                    .url(baseUrl.newBuilder().addPathSegment("api").build())
                    .post(HueJson.encodeToString(JsonObject.serializer(), body).toRequestBody(JSON))
                    .build()
                val text = client.newCall(req).execute().use { it.body?.string().orEmpty() }
                val first = HueJson.parseToJsonElement(text).jsonArray.firstOrNull()?.jsonObject
                    ?: throw HueApiException(0, "Unexpected response from bridge")
                first["success"]?.jsonObject?.get("username")?.jsonPrimitive?.content?.let { return@withContext it }
                val error = first["error"]?.jsonObject
                if (error?.get("type")?.jsonPrimitive?.content == "101") throw LinkButtonNotPressedException()
                throw HueApiException(0, error?.get("description")?.jsonPrimitive?.content ?: "Pairing failed")
            }
    }
}
