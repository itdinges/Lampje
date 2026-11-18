package app.lampje.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Recursively merges [patch] into [base]. Objects merge, everything else is replaced. */
fun deepMerge(base: JsonObject, patch: JsonObject): JsonObject {
    val merged = base.toMutableMap()
    for ((key, value) in patch) {
        val existing = merged[key]
        merged[key] = if (existing is JsonObject && value is JsonObject) deepMerge(existing, value) else value
    }
    return JsonObject(merged)
}

/** Caches bridge state, keeps it fresh from the event stream and offers the write actions. */
class HueStore(private val api: HueApi) {
    private val _snapshot = MutableStateFlow(HueSnapshot())
    val snapshot: StateFlow<HueSnapshot> = _snapshot.asStateFlow()

    private val _error = MutableStateFlow<Throwable?>(null)
    val error: StateFlow<Throwable?> = _error.asStateFlow()

    suspend fun refresh() {
        val results = coroutineScope {
            TYPES.map { type ->
                async {
                    // Optional types (e.g. MotionAware on a non-Pro bridge) may be unavailable.
                    type to if (type in OPTIONAL_TYPES) runCatching { api.list(type) }.getOrDefault(emptyList()) else api.list(type)
                }
            }.awaitAll()
        }
        _snapshot.value = HueSnapshot(
            results.associate { (type, items) -> type to items.associateBy { it["id"]!!.jsonPrimitive.content } },
        )
    }

    /** Loads state and then follows the event stream, reconnecting until cancelled. */
    suspend fun follow(retryDelayMs: Long = 3_000) {
        while (true) {
            try {
                refresh()
                _error.value = null
                api.events().collect { events -> events.forEach(::apply) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e
            }
            delay(retryDelayMs)
        }
    }

    fun apply(event: HueEvent) {
        _snapshot.update { current ->
            val raw = current.raw.toMutableMap()
            for (item in event.data) {
                val id = item["id"]?.jsonPrimitive?.content ?: continue
                val type = item["type"]?.jsonPrimitive?.content ?: continue
                val bucket = raw[type].orEmpty().toMutableMap()
                when (event.type) {
                    "delete" -> bucket.remove(id)
                    "update" -> bucket[id] = bucket[id]?.let { deepMerge(it, item) } ?: item
                    "add" -> bucket[id] = item
                }
                raw[type] = bucket
            }
            HueSnapshot(raw)
        }
    }

    private fun patchLocally(type: String, id: String, patch: JsonObject) {
        apply(HueEvent("update", listOf(buildJsonObject {
            put("id", id)
            put("type", type)
            patch.forEach { (k, v) -> put(k, v) }
        })))
    }

    private suspend fun write(type: String, id: String, body: JsonObject) {
        api.update(type, id, body)
        patchLocally(type, id, body)
    }

    private fun onBody(on: Boolean) = buildJsonObject { put("on", buildJsonObject { put("on", on) }) }

    private fun dimBody(brightness: Double) =
        buildJsonObject { put("dimming", buildJsonObject { put("brightness", brightness.coerceIn(0.0, 100.0)) }) }

    suspend fun setLightOn(id: String, on: Boolean) = write("light", id, onBody(on))
    suspend fun setLightBrightness(id: String, brightness: Double) = write("light", id, dimBody(brightness))
    suspend fun setGroupOn(groupedLightId: String, on: Boolean) = write("grouped_light", groupedLightId, onBody(on))
    suspend fun setGroupBrightness(groupedLightId: String, brightness: Double) =
        write("grouped_light", groupedLightId, dimBody(brightness))

    suspend fun recallScene(id: String) {
        api.update("scene", id, buildJsonObject { put("recall", buildJsonObject { put("action", "active") }) })
    }

    suspend fun setAutomationEnabled(id: String, enabled: Boolean) =
        write("behavior_instance", id, buildJsonObject { put("enabled", enabled) })

    suspend fun setMotionAreaEnabled(id: String, enabled: Boolean) =
        write("motion_area_configuration", id, buildJsonObject { put("enabled", enabled) })

    /** Zones may contain lights from different rooms, which is what enables cross-room MotionAware. */
    suspend fun createZone(name: String, lightIds: Collection<String>) {
        api.create("zone", zoneBody(name, lightIds))
        refresh()
    }

    suspend fun updateZone(id: String, name: String, lightIds: Collection<String>) {
        api.update("zone", id, zoneBody(name, lightIds))
        refresh()
    }

    suspend fun deleteZone(id: String) {
        api.delete("zone", id)
        refresh()
    }

    private fun zoneBody(name: String, lightIds: Collection<String>) = buildJsonObject {
        put("metadata", buildJsonObject {
            put("name", name)
            put("archetype", "other")
        })
        put("children", buildJsonArray {
            lightIds.forEach { add(buildJsonObject { put("rid", it); put("rtype", "light") }) }
        })
    }

    companion object {
        val TYPES = listOf(
            "light", "device", "room", "zone", "grouped_light", "scene",
            "behavior_instance", "motion_area_configuration",
        )
        private val OPTIONAL_TYPES = setOf("motion_area_configuration")
    }
}
