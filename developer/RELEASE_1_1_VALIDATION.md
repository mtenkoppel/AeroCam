# AeroCam 1.1 release validation

## Automation regression

The 1.1 beta source was replayed through the production Karoo integration,
service, command queue and protocol encoder. This exposed lost automatic-stop
ownership on Karoo auto-pause. The fix preserves confirmed ownership across
auto-pause while preventing new captures during the pause. Manual pause and
ride end still relinquish automatic control.

A private FIT replay with explicitly captured rider settings produced seven
start/stop pairs after the fix. A photo-mode replay with a 120-second interval
produced nine shutter commands. Reversing callback order gave the same command
results. Synthetic overlapping gradient/heart-rate triggers did not stop early.
The private FIT, settings, route and output logs are not distributed here.

The replay substitutes camera transport and acknowledgements; it verifies command
emission, not successful physical recording or saved JPEG files. Physical camera,
Bluetooth and firmware behavior remain outside this evidence.

## Reusing the tool

See [the replay guide](../scripts/ride-replay/README.md). AGENTS.md points future
agents to this production-code harness. It skips normal CI unless a private
input directory is supplied.

## Public screenshots

The PNGs in `landingpage/images/release-1.1` were regenerated from the Android
layouts at source a91ec4b using Robolectric native graphics (SDK 32, 300 dpi).
The 1.1.0 version bump does not change these layouts. Camera states are fixtures,
not photographs or evidence of a live camera connection. No private route data
is included.

Sources in `app/build/reports/settings-ui`:

- `AutomationWidgetUiTest`: `automation-widgets/map-combined-caption-false.png`,
  `map-combined-recording.png`, `map-combined-photos-idle.png`.
- `CamControlUiTest`: `cam-control/dark-xl-600-ready.png`.
- `SettingsUiTest`: `settings-light.png`, `automation-catalog.png`.
- `OnboardingTriggersUiTest`: `onboarding/automation-mode-light.png`.

Local `testDebugUnitTest assembleDebug`: 152 tests, zero failures/errors,
two opt-in replay tests skipped. The local build used public Karoo SDK source
at its exact 1.1.9 tag because package credentials were unavailable locally.
The release CI uses the declared published SDK dependency and established
signing key, and verifies package, version and non-debuggable APK status.
