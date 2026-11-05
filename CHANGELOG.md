# Changelog

Record rider-visible changes under Unreleased as they land. Move the entries to
the matching version heading when preparing a release.

## [Unreleased]

- Added an Automation section with one choice of automatic video highlights, automatic recording, or automatic photos, plus a trigger catalog and editable trigger details.
- Automatic recordings stop after a full quiet minute by default. Renewed efforts reset the delay; the delay is adjustable and automatic stopping can be disabled. Automatic highlights default on.
- Added AUTO START / AUTO STOP sublines and brief REC STARTED / REC STOPPED feedback to the shared video control. Manual recordings remain under rider control.
- Automatic photos use a 30-second minimum interval and skip recording, busy, or unavailable cameras without replaying events later.

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
