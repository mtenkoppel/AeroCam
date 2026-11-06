# Replay FIT rides through production automation

Use this for: “With these settings, what camera commands does this code send on
this ride?” It tests the implementation, not a separate model of its intended rules.

`RideReplayTest` feeds serialized Karoo SDK sensor and ride-state events into
`DjiKarooIntegration.start()` consumers. It runs real handler timers,
`DjiCameraController`, `DjiCameraService.onStartCommand`, `DjiOsmoBleClient`, its
command queue, protocol encoder, recording ownership and status parser. A fake
camera replaces physical Bluetooth I/O and acknowledges valid commands. Commands
are logged only when they reach that transport boundary.

## Run locally

Prerequisites: the normal Android/JDK/Gradle project toolchain and Karoo SDK package
access (see the project build instructions), plus Python 3. Use an output directory
**outside this repository**; results contain precise locations and sensor readings.

```sh
python3 -m venv /tmp/aerocam-replay-python
/tmp/aerocam-replay-python/bin/pip install -r scripts/ride-replay/requirements.txt
/tmp/aerocam-replay-python/bin/python scripts/ride-replay/decode_fit.py \
  /absolute/path/ride.fit /absolute/private/replay
cp scripts/ride-replay/settings.example.json /absolute/private/replay/settings.json
# Edit settings.json to the settings you want to test, then:
AEROCAM_REPLAY_DIR=/absolute/private/replay ./gradlew :app:testDebugUnitTest \
  --tests 'com.karoopoc.dji.RideReplayTest' --rerun-tasks
```

The `--rerun-tasks` option prevents Gradle from reusing a result from another private
fixture/configuration. When `AEROCAM_REPLAY_DIR` is unset, these two tests skip.
No connected Karoo is required; this does not modify device settings.

## Settings and supported input

- `mode`: `recording` or `photos` (use separate output directories for comparisons).
- `photoIntervalSeconds`: minimum interval, 120 by default. This is a minimum spacing
  between events, not a repeating photo timer while a condition remains true.
- `autoStop`, `stopAfterSeconds`, `highlights`: normal video settings.
- `speedKph`, `heartRateBpm`, `uphillGrade`, `downhillGrade`: include a key to enable
  that trigger; remove the key to disable it. Grade is signed percent, speed km/h.
- `uphillRequireSpeed` / `uphillSpeedKph`, `downhillRequireSpeed` / `downhillSpeedKph`:
  optional combined gradient/speed conditions, matching production settings.
- `honorPauses`: keep true for the primary replay. False is a diagnostic comparison,
  not evidence of what the original ride produces.

Currently supports recorded speed, HR and grade thresholds. Power, lap, climb
summit and ride-boundary trigger replay are not implemented; do not claim coverage
for those. GPS is used for plotting only. Missing grade is not synthesized.
The decoder refuses duplicate/subsecond record timestamps instead of silently
resampling them. Timer stop events preserve manual versus automatic pauses.
The fixture starts an active ride at the first record; session completion after
the final sensor sample becomes `RideState.Idle`.

## Results and checks

- `production-recording.tsv` or `production-photos.tsv`: elapsed milliseconds,
  event kind, detail/reason, owned recording ID.
- `production-*-reverse.tsv`: another full replay with reversed simultaneous
  sensor update order. Compare START/STOP/PHOTO rows, not incidental queue logs.
- `START` / `STOP`: encoded recording-control frames accepted at the simulated
  transport boundary.
- `PHOTO`: encoded photo shutter key-report frame, verified in photo mode.
- `PHOTO_ACCEPTED`: automation accepted a photo request (not yet proof of shutter).
- `PHOTO_SKIPPED`: a requested photo was not accepted; inspect spacing and state
  before attributing a cause. It may be interval limiting, camera busy/offline, etc.
- `WIRE`: actual encoded command bytes and validity outcome.
- `HANDOVER`: a recording remains active but automatic ownership was relinquished.
- `END`: camera state at fixture end. This is not a stop command.

Reasons come from the production request's `EXTRA_AUTOMATION_REASON` metadata.
The photo reason is associated with the accepted request, not a later rejected event.
The tests assert no video START in photo mode, photo spacing, valid photo mode at
shutter, and no redundant START/STOP state transition. A zero-command ride is valid.
Keep baseline logs before fixing a bug, rerun the unchanged original input after
the fix, and retain commit/configuration/test evidence with the private output.

For a map, use decoded FIT positions at each emitted command timestamp (interpolate
only between available GPS fixes), the recorded reason, and stop-start duration for
video. Use a sourced basemap; never upload the FIT to get a map. An agent can build
an interactive map from these outputs. The harness itself makes no map requests.

## Limits

This is a Robolectric integration test, not a real Karoo/camera or radio test.
The fake camera is paired and connected, acknowledges writes immediately, and
publishes status once per second. Production's 700ms mode-switch delay and 1-second
automation tick run on virtual time in 100ms steps. No manual camera actions,
transport loss, camera processing delays or firmware failures are simulated.
A PHOTO command is not evidence that a physical JPEG was saved. Real command
positions can shift with live sensor timing and Bluetooth latency.

Private FIT recordings and generated maps must never enter Git or CI artifacts.
Only the generic harness, decoder, sample configuration and this documentation
belong in the repository.
