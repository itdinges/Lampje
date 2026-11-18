# Lampje

Open-source Android app for Philips Hue, aimed at power users. Local only (Wi-Fi), built for a Hue Bridge Pro using the CLIP v2 API.

## Goals
- Lights, rooms and zones: on/off, brightness, color, color temperature
- Scenes, dynamic scenes and smart scenes
- View and toggle existing automations (`behavior_instance`), editing later
- Zone editor, including zones spanning multiple rooms
- MotionAware (`motion_area_configuration`) on custom zones, e.g. toilet + hallway
- Better entertainment area editor (`entertainment_configuration`)

## Status
Early MVP: pairing, lights/rooms, scenes, zone editor (cross-room), automations on/off, MotionAware list and toggle.

## Structure
- `core/`: pure Kotlin/JVM Hue CLIP v2 client, state cache with live event stream (SSE), unit tests
- `app/`: Android app (Kotlin, Jetpack Compose, Material 3)

## Build
- Tests: `./gradlew :core:test` (JDK 17)
- APK: `./gradlew :app:assembleDebug` (needs the Android SDK, `ANDROID_HOME`). GitHub Actions builds it on every push; download `lampje-debug-apk` from the workflow run artifacts.

## Pairing
In the app enter the bridge IP, press the link button on the bridge and tap Pair. The application key and the bridge's certificate fingerprint (trust on first use) are stored in app-private storage.

See [docs/api-notes.md](docs/api-notes.md).

## License
Apache-2.0
