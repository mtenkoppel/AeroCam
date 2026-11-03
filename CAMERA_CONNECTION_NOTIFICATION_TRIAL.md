# Camera connection notification trial

This branch announces a connected DJI camera through Karoo's standard
`SystemNotification` API, headed AeroCam. It reuses the existing translated
“DJI camera connected” message in all six languages. It works independently of
ride recording and the optional photo/recording action alerts.

Only entry into CONNECTED sends the notification. Subsequent battery, storage,
and recording updates do not repeat it. A reconnect sends it again using the
same notification ID, updating the existing Control Center entry. Starting
observation with an already connected camera announces it once.

No BLE transport, connection lifecycle, dependencies, or signing changes.

## Compatibility and verification

- Existing SDK: `io.hammerhead:karoo-ext:1.1.9`; no upgrade.
- Official release requirement: Karoo KOS 1.634.2440 or newer. Android minSdk
  23 is a separate requirement and does not identify the Karoo OS version.
- API checked 2026-11-03:
  https://hammerheadnav.github.io/karoo-ext/karoo-ext/io.hammerhead.karooext.models/-system-notification/index.html
- SDK release notes: https://github.com/hammerheadnav/karoo-ext/releases/tag/1.1.9
- Added automated coverage: connection edge policy tests. Required validation:
  the existing unit suite (`testDebugUnitTest`) and debug assembly (`assembleDebug`).
  The local attempt could not start because no Java runtime is configured.
  Cloud validation is pending approval to push this branch; no passing build
  or test run is claimed.
- Physical device/firmware tested for this change: none yet. No installation
  or release publication is part of this trial implementation.

## Device acceptance check

1. With no ride recording, turn on the paired camera and confirm the normal
   Karoo notification says “DJI camera connected” under AeroCam.
2. Leave it connected while battery and camera status update: no repeats.
3. Disconnect and reconnect: one fresh notification, with no stacked duplicates
   in Control Center. Failed connection attempts should not notify.
4. Repeat during an active ride and while paused; confirm the standard Karoo
   presentation is useful and unobtrusive. Check the Control Center entry and
   banner behavior on the actual device; the platform controls presentation.
5. Confirm existing photo, recording, and low-battery alerts still behave as
   before. Record Karoo model/KOS and camera model/firmware with the results.
