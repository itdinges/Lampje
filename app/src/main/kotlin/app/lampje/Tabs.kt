package app.lampje

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lampje.core.BehaviorInstance
import app.lampje.core.Group
import app.lampje.core.HueSnapshot
import app.lampje.core.Light

private val screenPadding = PaddingValues(12.dp)
private val itemSpacing = Arrangement.spacedBy(12.dp)

@Composable
private fun BrightnessSlider(value: Double?, onCommit: (Double) -> Unit) {
    var local by remember(value) { mutableFloatStateOf((value ?: 1.0).toFloat().coerceIn(1f, 100f)) }
    Slider(
        value = local,
        onValueChange = { local = it },
        onValueChangeFinished = { onCommit(local.toDouble()) },
        valueRange = 1f..100f,
    )
}

@Composable
private fun LightRow(light: Light, vm: MainViewModel) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(light.metadata.name, Modifier.weight(1f))
            Switch(checked = light.on.on, onCheckedChange = { on -> vm.act { setLightOn(light.id, on) } })
        }
        if (light.dimming != null && light.on.on) {
            BrightnessSlider(light.dimming?.brightness) { vm.act { setLightBrightness(light.id, it) } }
        }
    }
}

@Composable
fun LightsTab(snapshot: HueSnapshot, vm: MainViewModel) {
    val unassigned = snapshot.lights.filter { snapshot.roomOf(it) == null }
    LazyColumn(contentPadding = screenPadding, verticalArrangement = itemSpacing) {
        items(snapshot.rooms, key = { it.id }) { room ->
            val grouped = snapshot.groupedLightOf(room)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(room.metadata.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        if (grouped != null) {
                            Switch(checked = grouped.on.on, onCheckedChange = { on -> vm.act { setGroupOn(grouped.id, on) } })
                        }
                    }
                    if (grouped?.dimming != null && grouped.on.on) {
                        BrightnessSlider(grouped.dimming?.brightness) { vm.act { setGroupBrightness(grouped.id, it) } }
                    }
                    snapshot.lightsIn(room).forEach { LightRow(it, vm) }
                }
            }
        }
        if (unassigned.isNotEmpty()) {
            item(key = "unassigned") {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Other lights", style = MaterialTheme.typography.titleMedium)
                        unassigned.forEach { LightRow(it, vm) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScenesTab(snapshot: HueSnapshot, vm: MainViewModel) {
    val names = (snapshot.rooms + snapshot.zones).associate { it.id to it.metadata.name }
    val byGroup = snapshot.scenes.groupBy { it.group?.rid }.entries.toList()
    LazyColumn(contentPadding = screenPadding, verticalArrangement = itemSpacing) {
        items(byGroup, key = { it.key ?: "none" }) { (groupId, scenes) ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(names[groupId] ?: "Unknown", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        scenes.forEach { scene ->
                            val active = scene.status?.active.let { it != null && it != "inactive" }
                            FilterChip(
                                selected = active,
                                onClick = { vm.act { recallScene(scene.id) } },
                                label = { Text(scene.metadata.name) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ZoneDraft(val id: String?, val name: String, val lightIds: Set<String>)

@Composable
fun ZonesTab(snapshot: HueSnapshot, vm: MainViewModel) {
    var draft by remember { mutableStateOf<ZoneDraft?>(null) }
    var deleting by remember { mutableStateOf<Group?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 88.dp),
            verticalArrangement = itemSpacing,
        ) {
            items(snapshot.zones, key = { it.id }) { zone ->
                val lights = snapshot.lightsIn(zone)
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(zone.metadata.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                lights.joinToString { it.metadata.name }.ifEmpty { "No lights" },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(onClick = { draft = ZoneDraft(zone.id, zone.metadata.name, lights.map { it.id }.toSet()) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit zone")
                        }
                        IconButton(onClick = { deleting = zone }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete zone")
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { draft = ZoneDraft(null, "", emptySet()) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Default.Add, contentDescription = "New zone") }
    }

    draft?.let { current ->
        ZoneEditor(
            snapshot = snapshot,
            initial = current,
            onDismiss = { draft = null },
            onSave = { saved ->
                draft = null
                vm.act {
                    if (saved.id == null) createZone(saved.name, saved.lightIds) else updateZone(saved.id, saved.name, saved.lightIds)
                }
            },
        )
    }

    deleting?.let { zone ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete zone?") },
            text = { Text("\"${zone.metadata.name}\" will be removed. The lights stay as they are.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    vm.act { deleteZone(zone.id) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ZoneEditor(snapshot: HueSnapshot, initial: ZoneDraft, onDismiss: () -> Unit, onSave: (ZoneDraft) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var selected by remember { mutableStateOf(initial.lightIds) }
    // Lights are listed per room, but a zone may mix lights from any rooms.
    val byRoom = snapshot.lights.groupBy { snapshot.roomOf(it)?.metadata?.name ?: "No room" }.toSortedMap()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == null) "New zone" else "Edit zone") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Zone name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    byRoom.forEach { (room, lights) ->
                        item(key = "room-$room") {
                            Text(room, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge)
                        }
                        items(lights, key = { it.id }) { light ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = light.id in selected,
                                    onCheckedChange = { on -> selected = if (on) selected + light.id else selected - light.id },
                                )
                                Text(light.metadata.name)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && selected.isNotEmpty(),
                onClick = { onSave(initial.copy(name = name.trim(), lightIds = selected)) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AutomationsTab(snapshot: HueSnapshot, vm: MainViewModel) {
    if (snapshot.automations.isEmpty()) {
        EmptyHint("No automations found on the bridge.")
        return
    }
    LazyColumn(contentPadding = screenPadding) {
        items(snapshot.automations, key = { it.id }) { automation ->
            ListItem(
                headlineContent = { Text(automation.displayName()) },
                supportingContent = { Text(automation.status ?: "unknown") },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.act { startAutomation(automation.id) } }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Run ${automation.displayName()} now")
                        }
                        Switch(
                            checked = automation.enabled,
                            onCheckedChange = { on -> vm.act { setAutomationEnabled(automation.id, on) } },
                        )
                    }
                },
            )
        }
    }
}

private fun BehaviorInstance.displayName() = metadata.name.ifBlank { "Automation ${id.take(8)}" }

@Composable
fun MotionTab(snapshot: HueSnapshot, vm: MainViewModel) {
    val names = (snapshot.rooms + snapshot.zones).associate { it.id to it.metadata.name }
    if (snapshot.motionAreas.isEmpty()) {
        EmptyHint(
            "No MotionAware areas found. MotionAware needs a Bridge Pro. " +
                "Create a zone with the lights that share line of sight (they can be in different rooms) first.",
        )
        return
    }
    LazyColumn(contentPadding = screenPadding) {
        items(snapshot.motionAreas, key = { it.id }) { area ->
            ListItem(
                headlineContent = { Text(area.displayName) },
                supportingContent = { Text(listOfNotNull(names[area.group?.rid], area.health).joinToString(" · ")) },
                trailingContent = {
                    Switch(
                        checked = area.enabled,
                        onCheckedChange = { on -> vm.act { setMotionAreaEnabled(area.id, on) } },
                    )
                },
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
