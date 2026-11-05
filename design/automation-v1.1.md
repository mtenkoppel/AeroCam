# Automation 1.1 implementation and validation

## Review and merge order

1. PR #62 (`codex/issue-59-widget-consistency`) contains #53/#58/#59: Cam Control, independent button alignment, shared action layouts and measured timer placement.
2. PR #61 (`codex/automation-v1.1`) builds on that branch. After #62 merges, retarget #61 to master. If the base is squash-merged, rebase the automation-only commits onto the merged master first to avoid duplicating the refactor diff.
3. Automatic photos (#52) are in #61 because the mode picker, trigger configuration and dispatcher are shared. Onboarding remains #60. No public release has been authorized or published.

## Agreed UI

One Automation card contains Enable automation, mutually exclusive native radio choices, Auto recording settings when relevant, and the trigger list. More information opens a page. Trigger switches preserve their saved thresholds and selection. Detail pages use the existing Karoo Back and Add/Save tabs; Back discards edits. Remove trigger is a centered ghost button.

The shared video button shows AUTO START below its idle icon when automatic recording is enabled. A camera-confirmed automatic start shows the timer with REC STARTED for five seconds, then AUTO STOP only for an automatically owned clip with stopping enabled. A confirmed automatic stop shows REC STOPPED for five seconds, then returns to the idle cue. Manual clips have no AUTO STOP promise. With no secondary line the timer recenters. Recording buttons no longer contain SD capacity. Existing glyphs, surfaces and measured shared layout are retained; compact idle layouts borrow icon padding to fit the subline.

## Algorithm and conservative choices

- Default stop delay: 60 seconds, editable from 10 to 3600 seconds. Add automatic highlights defaults on. Existing master, auto-start and trigger choices are read compatibly.
- Reuse existing threshold hysteresis. Any enabled active threshold holds the clip open. All enabled thresholds must be known inactive for a full delay. An event resets the delay even when highlight cooldown suppresses its marker.
- Telemetry older than ten seconds or explicitly unavailable is unknown, never an inferred exit. A configured but unavailable sensor can therefore keep an automatic clip running indefinitely. Disable unused triggers or stop manually.
- Only a confirmed transition within the bounded automatic-start request window establishes clip ownership. Failed writes, rejected record control, manual camera actions, disconnects and restarts revoke it. Ownership is not persisted.
- A queued automatic stop carries clip identity and generation guards. Renewed effort cancels a queued stop while preserving ownership. Automatic operations and queued writes have freshness checks; stale intents and delayed operations are discarded rather than replayed after reconnect.
- Pausing or ending the ride revokes automatic control and leaves an ongoing recording running manually. Eligible ride-end highlights retain the existing boundary behavior. Resume does not take ownership of that clip again.
- Editing automation settings also relinquishes the current clip. Manual highlight actions do not cancel recording ownership.
- Photos use the existing switch-to-photo, 700 ms settling delay, and shutter sequence. There is one shared minimum interval of 30 seconds; sustained threshold conditions do not retrigger until reset. Busy, recording and unavailable cameras are skipped. The existing manual video start explicitly switches back to video mode.

## Automated checks

Pure policy tests cover full delay, rebound, overlapping active conditions, event resets, unknown telemetry, custom delay, manual clips, new clip identities and photo throttling. Client tests exercise duplicate starts, failed writes, offline discard, clip guards, pause cancellation, renewed effort during a queued stop, photo suppression and settings migration. Native UI tests cover draft validation, cancel behavior, disabled trigger persistence, default values, appearance, and recording sublines at compact and full sizes. Existing protocol, privacy, ride-boundary, widget alignment and shared-control tests remain part of CI.

## Physical validation and risk

Target device inspected: Karoo 3, KOS **1.665.2548**, existing installed versionCode **48**. SDK remains **karoo-ext 1.1.9**, whose official release requires KOS 1.634.2440 or later. No SDK, build-toolchain or reconnect/lifecycle redesign is included.

The camera was unavailable during development. Do not call the feature ride-validated based on policy tests or rendered screenshots. Overall pre-hardware risk is **medium**, with highest uncertainty in photo mode switching and the interaction between asynchronous camera status and recording commands.

The camera protocol does not identify who pressed Record. A near-simultaneous external camera-button start during an accepted automatic start window cannot be distinguished with certainty. After disconnect/restart the safe behavior is to leave recording under manual control. This favors longer clips over stopping a manual recording. Sensor dropouts can also extend clips and battery/storage use.

Before releasing 1.1, perform a stationary camera test followed by at least two representative rides:

1. Trigger start; verify confirmed feedback, timer and the full 60-second quiet stop. Repeat with a custom delay and stopping disabled.
2. Re-enter a threshold just before expiry; verify a fresh full delay, overlapping triggers and hysteresis jitter. Repeat while automatic marker cooldown is active.
3. Start/stop manually from both Karoo and camera. Verify manual clips never show AUTO STOP and cannot inherit an old timer.
4. Pause, finish, disconnect, reconnect and restart the app during an automatic clip. Confirm there is no delayed start, stop or shutter. Verify ride-end marker ordering.
5. Drop or disable a configured sensor and verify recording is not prematurely stopped.
6. Test photos from idle video and photo modes, simultaneous triggers and sustained effort; check no burst, no capture during video, and return to manual video recording.
7. Check low storage, failed commands, camera power-off and rapid manual override. Review the actual saved files and markers, not only the Karoo UI.

Use the signed test APK as an in-place update; preserve pairing/settings and do not uninstall or clear data. Onboarding and final release-copy review remain release follow-ups.
