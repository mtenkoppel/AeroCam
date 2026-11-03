# Camera connection notification trial

This branch announces a connected DJI camera through Karoo's `InRideAlert`
API, using the same helper as the existing photo notifications: green background,
white text, and automatic dismissal after two seconds. It reuses the translated
“DJI camera connected” message in all six languages. The connection alert is
enabled independently of the optional photo/recording action alerts.

Only entry into CONNECTED sends the notification. Subsequent battery, storage,
and recording updates do not repeat it. A reconnect sends it again using the
same alert ID. Like photo alerts, it only displays during an actively recording
ride; idle and paused rides are suppressed. Connections made outside an active
ride are not queued for later. Reconnect the camera during an active ride to test.
This replaces the earlier Control Center notification with an in-ride popup.

No BLE transport, connection lifecycle, or dependency changes. The dedicated
debug trial uses the existing AeroCam release certificate so it can update the
installed app while retaining pairing and settings.

## Installable trial build

The branch's Signed test build workflow also runs
`./gradlew -PnotificationTrial=true assembleDebug`, verifies its signing
certificate, and uploads a separate `aerocam-notification-trial-debug-<commit>`
artifact. The APK is debuggable, uses application ID `com.karoopoc`, and has
version code 25 / version name `0.9.0-beta.3-notification.2-debug`.
This is an update to AeroCam, not a second camera controller. The notification
is always enabled on this experimental branch; no settings toggle is required.

The connected Karoo reported installed version code 24
(`0.9.0-beta.3-notification.1-debug`) before this update on 2026-11-03. This trial
is based on master and does not include the separate onboarding or combined
photo/video branches. It preserves their saved preferences but does not include
their experimental screens. Install only after a passing cloud build using
`adb install -r AeroCam-notification-trial-debug.apk`; never uninstall or clear
app data. Return to another branch with the same certificate and an equal or
higher version code. No automatic rollback or downgrade is attempted.

## Compatibility and verification

- Existing SDK: `io.hammerhead:karoo-ext:1.1.9`; no upgrade.
- Official release requirement: Karoo KOS 1.634.2440 or newer. Android minSdk
  23 is a separate requirement and does not identify the Karoo OS version.
- API checked 2026-11-03:
  https://hammerheadnav.github.io/karoo-ext/karoo-ext/io.hammerhead.karooext.models/-in-ride-alert/index.html
- SDK release notes: https://github.com/hammerheadnav/karoo-ext/releases/tag/1.1.9
- Added automated coverage: connection edge policy tests. Required validation:
  the existing unit suite (`testDebugUnitTest`) and debug assembly (`assembleDebug`).
  The local attempt could not start because no Java runtime is configured.
  Cloud validation runs on the dedicated branch; see its matching commit's
  Signed test build for the unit suite and both debug/release assembly results.
- Physical popup verification is pending on the user's Karoo (model k24).
  Camera firmware has not been recorded for this update. Cloud checks do not
  verify the physical popup experience. No public release is published.

## Device acceptance check

1. Start recording a ride on the Karoo, then connect the paired camera. Confirm
   a green “DJI camera connected” popup appears over the ride screen for two seconds.
2. Leave it connected while battery and camera status update: no repeats.
3. Disconnect and reconnect: one fresh popup. Failed connection attempts should
   not notify.
4. Repeat while paused and without a ride: no popup, matching the existing
   photo alert helper's ride-state guard. Resume recording and reconnect to
   confirm the next connection displays an alert.
5. Confirm existing photo, recording, and low-battery alerts still behave as
   before. Record Karoo model/KOS and camera model/firmware with the results.
