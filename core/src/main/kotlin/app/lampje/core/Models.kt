package app.lampje.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

val HueJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

@Serializable
data class ResourceRef(val rid: String, val rtype: String)

@Serializable
data class Metadata(val name: String = "", val archetype: String? = null)

@Serializable
data class OnState(val on: Boolean = false)

@Serializable
data class Dimming(val brightness: Double = 0.0)

@Serializable
data class ColorTemperature(val mirek: Int? = null)

@Serializable
data class Light(
    val id: String,
    val owner: ResourceRef? = null,
    val metadata: Metadata = Metadata(),
    val on: OnState = OnState(),
    val dimming: Dimming? = null,
    @SerialName("color_temperature") val colorTemperature: ColorTemperature? = null,
)

@Serializable
data class Device(
    val id: String,
    val metadata: Metadata = Metadata(),
    val services: List<ResourceRef> = emptyList(),
)

/** A room (children are devices) or a zone (children are lights). */
@Serializable
data class Group(
    val id: String,
    val metadata: Metadata = Metadata(),
    val children: List<ResourceRef> = emptyList(),
    val services: List<ResourceRef> = emptyList(),
)

@Serializable
data class GroupedLight(
    val id: String,
    val on: OnState = OnState(),
    val dimming: Dimming? = null,
)

@Serializable
data class SceneStatus(val active: String? = null)

@Serializable
data class Scene(
    val id: String,
    val metadata: Metadata = Metadata(),
    val group: ResourceRef? = null,
    val status: SceneStatus? = null,
)

@Serializable
data class BehaviorInstance(
    val id: String,
    @SerialName("script_id") val scriptId: String? = null,
    val enabled: Boolean = false,
    val status: String? = null,
    val metadata: Metadata = Metadata(),
    val configuration: JsonObject? = null,
)

/** MotionAware area (Bridge Pro). The exact schema is still to be verified on a real bridge. */
@Serializable
data class MotionAreaConfiguration(
    val id: String,
    val name: String? = null,
    val metadata: Metadata? = null,
    val enabled: Boolean = true,
    val group: ResourceRef? = null,
    val health: String? = null,
) {
    val displayName: String get() = name ?: metadata?.name ?: id
}
