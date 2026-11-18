# Hue CLIP v2 notes

Base: `https://<bridge>/clip/v2/resource/<type>`, header `hue-application-key`.
Pairing: press link button, then `POST http://<bridge>/api` with `{"devicetype":"lampje#device","generateclientkey":true}`.
Certificate is self-signed: pin the Hue root CA / bridge id.
Realtime: `GET /eventstream/clip/v2` (SSE).

## Resources of interest
| Feature | Resource |
|---|---|
| Lights | `light`, `grouped_light` |
| Rooms / zones | `room`, `zone` |
| Scenes | `scene`, `smart_scene` |
| Automations | `behavior_instance`, `behavior_script` |
| MotionAware | `motion_area_configuration`, `convenience_area_motion`, `security_area_motion` |
| Entertainment | `entertainment_configuration`, `entertainment` |

## Open questions (verify on the real bridge)
- Does `motion_area_configuration` accept a zone with lights from different rooms?
- Lamp limits per MotionAware area
- Writable fields per `behavior_script` (schemas differ per script)
- Entertainment channel limits for gradient lights
