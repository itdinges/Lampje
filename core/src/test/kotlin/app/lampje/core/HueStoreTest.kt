package app.lampje.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HueStoreTest {
    private val server = MockWebServer()
    private val bodies = mutableListOf<RecordedRequest>()

    private val fixtures = mapOf(
        "light" to """[
            {"id":"l1","type":"light","owner":{"rid":"d1","rtype":"device"},"metadata":{"name":"Wc"},"on":{"on":true},"dimming":{"brightness":40.0}},
            {"id":"l2","type":"light","owner":{"rid":"d2","rtype":"device"},"metadata":{"name":"Gang"},"on":{"on":false}}]""",
        "device" to """[{"id":"d1","type":"device"},{"id":"d2","type":"device"}]""",
        "room" to """[
            {"id":"r1","type":"room","metadata":{"name":"Toilet"},"children":[{"rid":"d1","rtype":"device"}],"services":[{"rid":"g1","rtype":"grouped_light"}]},
            {"id":"r2","type":"room","metadata":{"name":"Hallway"},"children":[{"rid":"d2","rtype":"device"}],"services":[]}]""",
        "grouped_light" to """[{"id":"g1","type":"grouped_light","on":{"on":true}}]""",
    )

    private fun store(): HueStore {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                bodies += request
                val type = request.path!!.substringAfter("/clip/v2/resource/").substringBefore("/")
                if (request.method == "GET") {
                    if (type == "motion_area_configuration") return MockResponse().setResponseCode(404).setBody("{}")
                    return MockResponse().setBody("""{"errors":[],"data":${fixtures[type] ?: "[]"}}""")
                }
                return MockResponse().setBody("""{"errors":[],"data":[{"rid":"new","rtype":"$type"}]}""")
            }
        }
        return HueStore(HueApi(server.url("/"), "secret", OkHttpClient()))
    }

    @AfterTest
    fun tearDown() = server.shutdown()

    @Test
    fun refreshBuildsSnapshotAndToleratesMissingMotionAware() = runTest {
        val store = store()
        store.refresh()
        val snap = store.snapshot.value
        assertEquals(listOf("Gang", "Wc"), snap.lights.map { it.metadata.name })
        assertEquals("Toilet", snap.roomOf(snap.lights.first { it.id == "l1" })?.metadata?.name)
        assertEquals(listOf("l1"), snap.lightsIn(snap.rooms.first { it.id == "r1" }).map { it.id })
        assertNotNull(snap.groupedLightOf(snap.rooms.first { it.id == "r1" }))
        assertTrue(snap.motionAreas.isEmpty())
        assertEquals("secret", bodies.first().getHeader("hue-application-key"))
    }

    @Test
    fun eventsMergeIntoCachedResources() = runTest {
        val store = store()
        store.refresh()
        val event = HueApi.parseEvents(
            """[{"type":"update","data":[{"id":"l1","type":"light","on":{"on":false}}]}]""",
        ).single()
        store.apply(event)
        val light = store.snapshot.value.lights.first { it.id == "l1" }
        assertEquals(false, light.on.on)
        assertEquals(40.0, light.dimming?.brightness)

        store.apply(HueEvent("delete", listOf(HueJson.parseToJsonElement("""{"id":"l1","type":"light"}""").jsonObject)))
        assertEquals(listOf("l2"), store.snapshot.value.lights.map { it.id })
    }

    @Test
    fun setLightOnSendsPutAndUpdatesOptimistically() = runTest {
        val store = store()
        store.refresh()
        store.setLightOn("l2", true)
        val put = bodies.last()
        assertEquals("PUT", put.method)
        assertEquals("/clip/v2/resource/light/l2", put.path)
        assertEquals("""{"on":{"on":true}}""", put.body.readUtf8())
        assertTrue(store.snapshot.value.lights.first { it.id == "l2" }.on.on)
    }

    @Test
    fun zoneCanSpanLightsFromDifferentRooms() = runTest {
        val store = store()
        store.refresh()
        store.createZone("Wc + Gang", listOf("l1", "l2"))
        val post = bodies.first { it.method == "POST" }
        assertEquals("/clip/v2/resource/zone", post.path)
        val json = HueJson.parseToJsonElement(post.body.readUtf8()).jsonObject
        assertEquals("Wc + Gang", json["metadata"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(listOf("l1", "l2"), json["children"]!!.jsonArray.map { it.jsonObject["rid"]!!.jsonPrimitive.content })
    }

    @Test
    fun apiErrorsAreSurfaced() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"errors":[{"description":"nope"}],"data":[]}"""))
        val api = HueApi(server.url("/"), "k", OkHttpClient())
        val e = assertFailsWith<HueApiException> { api.list("light") }
        assertEquals("nope", e.message)
    }

    @Test
    fun pairingReportsLinkButtonAndReturnsKey() = runTest {
        server.enqueue(MockResponse().setBody("""[{"error":{"type":101,"description":"link button not pressed"}}]"""))
        assertFailsWith<LinkButtonNotPressedException> { HueApi.pair(server.url("/"), OkHttpClient(), "lampje#test") }
        server.enqueue(MockResponse().setBody("""[{"success":{"username":"abc123","clientkey":"x"}}]"""))
        assertEquals("abc123", HueApi.pair(server.url("/"), OkHttpClient(), "lampje#test"))
    }

    @Test
    fun deepMergeKeepsUntouchedSiblings() {
        val base = HueJson.parseToJsonElement("""{"a":{"x":1,"y":2},"l":[1,2]}""") as JsonObject
        val patch = HueJson.parseToJsonElement("""{"a":{"y":3},"l":[9]}""") as JsonObject
        assertEquals("""{"a":{"x":1,"y":3},"l":[9]}""", deepMerge(base, patch).toString())
    }
}
