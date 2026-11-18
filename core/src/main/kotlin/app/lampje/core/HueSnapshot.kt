package app.lampje.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject

/** Immutable view of everything cached from the bridge, keyed by resource type then id. */
class HueSnapshot(val raw: Map<String, Map<String, JsonObject>> = emptyMap()) {
    val lights: List<Light> by lazy { decode(Light.serializer(), "light").sortedBy { it.metadata.name.lowercase() } }
    val devices: List<Device> by lazy { decode(Device.serializer(), "device") }
    val rooms: List<Group> by lazy { decode(Group.serializer(), "room").sortedBy { it.metadata.name.lowercase() } }
    val zones: List<Group> by lazy { decode(Group.serializer(), "zone").sortedBy { it.metadata.name.lowercase() } }
    val groupedLights: List<GroupedLight> by lazy { decode(GroupedLight.serializer(), "grouped_light") }
    val scenes: List<Scene> by lazy { decode(Scene.serializer(), "scene").sortedBy { it.metadata.name.lowercase() } }
    val automations: List<BehaviorInstance> by lazy {
        decode(BehaviorInstance.serializer(), "behavior_instance").sortedBy { it.metadata.name.lowercase() }
    }
    val motionAreas: List<MotionAreaConfiguration> by lazy {
        decode(MotionAreaConfiguration.serializer(), "motion_area_configuration")
    }

    fun groupedLightOf(group: Group): GroupedLight? {
        val rid = group.services.firstOrNull { it.rtype == "grouped_light" }?.rid ?: return null
        return groupedLights.firstOrNull { it.id == rid }
    }

    /** Lights belonging to a room (via its devices) or to a zone (direct children). */
    fun lightsIn(group: Group): List<Light> {
        val childIds = group.children.map { it.rid }.toSet()
        return lights.filter { it.id in childIds || it.owner?.rid in childIds }
    }

    /** Name of the room that contains this light, if any. */
    fun roomOf(light: Light): Group? = rooms.firstOrNull { room -> room.children.any { it.rid == light.owner?.rid } }

    private fun <T> decode(serializer: KSerializer<T>, type: String): List<T> =
        raw[type].orEmpty().values.mapNotNull {
            runCatching { HueJson.decodeFromJsonElement(serializer, it) }.getOrNull()
        }
}
