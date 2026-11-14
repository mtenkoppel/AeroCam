# Changelog

Record rider-visible changes under Unreleased as they land. Move the entries to
the matching version heading when preparing a release.

## [Unreleased]

- Added the beta-only **Hardware start/stop hack** under Advanced settings. Karoo lap events toggle video recording when enabled, overriding AeroCam’s lap automation.

## [1.1.0] - 2026-11-06

AeroCam 1.1 brings automatic recording and photos, guided setup, and polished ride controls.

- Choose automatic recording, automatic video highlights, or automatic photos, then add the ride triggers that matter to you.
- Automatic recordings stop after a quiet minute by default. Renewed triggers keep the clip running. Adjust the delay or disable automatic stopping in Auto recording settings; manual recordings remain under your control.
- Set the minimum time between automatic photos in Auto photo settings (two minutes by default). Busy, recording or unavailable cameras skip photos rather than queue them.
- Follow the new two-step automation onboarding: choose a mode, then add and edit triggers. You can skip automation and configure it later.
- An A inside the video/photo icon indicates the armed automatic mode. AUTO REC beneath the timer identifies a clip under automatic-stop control, with brief start/stop feedback.
- Shared camera controls now use consistent layouts, readable labels and centered content across widget sizes. Cam Control shows video resolution alongside battery and storage status.
- Simpler automation help, compact settings, and shared trigger cards across setup and Settings, localized in all six supported languages.

- Fixed Karoo auto-pause relinquishing control of an automatically started recording. Auto-pause now preserves automatic stopping while suppressing new captures; manually pausing or ending the ride still returns control to the rider.
- Added a local FIT replay tool for debugging real automation command output. Regression checks cover auto-pause, overlapping triggers, manual overrides and automatic photo intervals.

Install over your existing AeroCam app to keep camera pairing and settings.


## [1.1.0-beta.1] - 2026-11-06

This is the first official 1.1 beta. The stable release remains 1.0.2.

- Choose automatic recording, automatic video highlights, or automatic photos, then add the ride triggers that matter to you.
- Automatic recordings stop after a quiet minute by default. Renewed triggers keep the clip running. Adjust the delay or disable automatic stopping in Auto recording settings; manual recordings remain under your control.
- Set the minimum time between automatic photos in Auto photo settings (two minutes by default). Busy, recording or unavailable cameras skip photos rather than queue them.
- Follow the new two-step automation onboarding: choose a mode, then add and edit triggers. You can skip automation and configure it later.
- An A inside the video/photo icon indicates the armed automatic mode. AUTO REC beneath the timer identifies a clip under automatic-stop control, with brief start/stop feedback.
- Shared camera controls now use consistent layouts, readable labels and centered content across widget sizes. Cam Control shows video resolution alongside battery and storage status.
- Simpler automation help, compact settings, and shared trigger cards across setup and Settings, localized in all six supported languages.

Install this APK over your existing AeroCam installation to keep pairing and settings. Do not uninstall first.

Beta testing: camera commands and ride automation need real-world validation. In particular, test overlapping triggers, manual start/stop overrides, reconnects and switching between photos and video. Configure automation before riding and review results when stopped.

## [1.0.2] - 2026-11-05

- Trimmed diagnostic logs to omit pairing details, device identifiers, and raw Bluetooth and GPS data. Connection events and error codes are still available for troubleshooting.

## [1.0.1] - 2026-11-05

- Clarified how to install and update AeroCam on Karoo.
- Added screenshots showing how camera controls look at different data field sizes.
- Made the README and website download links follow the latest stable release.

Camera controls and recording work as in v1.0.

## [1.0] - 2026-11-04

- Revised camera pairing and onboarding, with clearer re-pair and skip actions
  and Karoo-style navigation.
- Improved ride data field labels, alignment, button sizing and backgrounds.
- Made Cam Control settings available at every full-width size and added a
  battery and storage view for half-width fields.
- Added optional minimum speed conditions for ascent and descent triggers.
- Moved the automatic highlight cooldown to Advanced settings.
- Improved disconnected camera controls and streamlined in-app help.

See the [v1.0 release](https://github.com/mtenkoppel/AeroCam/releases/tag/v1.0)
for downloads and earlier GitHub releases for beta notes.
