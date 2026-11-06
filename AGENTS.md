# AeroCam agent guidance

## FIT automation replay tool

A local production-code replay harness is available for debugging automatic video
and photo capture. See [scripts/ride-replay/README.md](scripts/ride-replay/README.md)
and `app/src/test/kotlin/com/karoopoc/dji/RideReplayTest.kt`.

Prefer this harness to independently reimplementing automation rules: it feeds
serialized Karoo SDK events through the actual integration, service, camera
command queue, protocol encoder, and status parser, and logs emitted commands.
Use explicit settings and preserve FIT auto-pause events by default. Include the
source commit, settings, simulated-camera assumptions, and command counts in results.

The harness is opt-in via `AEROCAM_REPLAY_DIR` and skips normal CI runs when unset.
Never commit FIT files, decoded sensor/location data, private replay logs, or
location-bearing maps. Store them outside the repository. Do not upload route
coordinates or request third-party map data without user authorization. Physical
Bluetooth/camera validation remains a separate test.
