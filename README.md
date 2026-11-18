# Lampje

Open-source Android app for Philips Hue, aimed at power users. Local only (Wi-Fi), built for a Hue Bridge Pro using the CLIP v2 API.

## Goals
- Lights, rooms and zones: on/off, brightness, color, color temperature
- Scenes, dynamic scenes and smart scenes
- View and toggle existing automations (`behavior_instance`), editing later
- Zone editor, including zones spanning multiple rooms
- MotionAware (`motion_area_configuration`) on custom zones, e.g. toilet + hallway
- Better entertainment area editor (`entertainment_configuration`)

## Planned stack
Kotlin, Jetpack Compose (Material 3), OkHttp + kotlinx.serialization, SSE eventstream, Hilt, Room.

## Bridge configuration
Bridge address and application key are configured locally (never committed):
`lampje.properties`

```
HUE_BRIDGE_IP=192.168.x.x
HUE_APP_KEY=...
```

If no IP is set, discovery via mDNS (`_hue._tcp`) is used.

See [docs/api-notes.md](docs/api-notes.md).

## License
Apache-2.0
