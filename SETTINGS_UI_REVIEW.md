# Settings UI refactor

Branch: `codex/settings-ui-native`, based on `68bc3d5`.

## Scope and defaults

This change restructures the settings, status, Help, and setup presentation. It does not modify `DjiSettings`, the camera service/controller, BLE client, automation integration, widget rendering, pairing credentials, or preference keys/defaults. No settings migration runs. Setup reads current values; opening, skipping, going back, or finishing does not reset preferences. Only explicit user changes write settings.

Controls are Android `Switch`, `Spinner`, `EditText`, `Button`, `Toolbar`, `ScrollView`, and `AlertDialog`. Help opens in a separate native activity using the platform back stack and `Slide` activity transition. The surrounding card layout and three existing tabs remain simple views, without new component frameworks. The switches and dialogs inherit the Android theme's accent; no sampled Karoo color is hardcoded.

Both light and dark are supported through the existing theme preference and system-mode behavior. Settings colors are locally monochrome; widget colors and rendering remain unchanged.

## Preserved handling

- Existing preference keys, defaults, threshold ranges and numeric fallback/clamping.
- Auto-highlights master visibility and conditional threshold inputs; disabling does not erase child settings.
- Keep-awake controller setter, stored interval and range, BLE readiness/reconnect delay, pause during recording, recovery after recording, and sleep behavior.
- Bluetooth permission differences before/after Android 12, denied-permission feedback, and retry from setup/search.
- Discovery/search/accept states, 10-second search latch, and use of an already-paired camera when replaying setup.
- Camera observer cancellation on activity destruction. Setup has its own observer while visible.
- Storage display with unknown capacity or remaining time, and all existing runtime standby status values.
- Existing reset behavior (clear preferences, forget pairing, refresh widgets); now with unchanged native confirmation. Forget gains its own native confirmation and leaves other settings intact.
- Help language fallback. Restructured topics use new filenames so stale localized first/standby/widget articles do not silently reappear. New copy falls back to English until translated.
- Appearance selection and caption changes still refresh widgets; appearance still recreates the activity. Selected tab and native scroll state survive recreation.

The Status rows reserve fixed line counts: three for debug, two for the other values. The connection card reserves its setup controls' space with `INVISIBLE` rather than changing its height during state transitions.

## Deliberately deferred findings

1. High-speed descent automation exists in the runtime/preferences and Help, but has never had a switch in this settings screen. No new setting or default is introduced here.
2. Ride-end bookmarks do not check the auto-highlights master in the same way as other triggers. Existing runtime behavior is unchanged.
3. Photo/recording alert controls are removed from the UI as approved. Their saved preference and runtime behavior remain unchanged under the final UI-only constraint; feature removal is tracked separately in GitHub issue #26. Users who already enabled them will retain them until that separate change.
4. The browser onboarding draft proposed deriving a keep-awake interval from the camera standby timer. This would change behavior. Native setup keeps the existing stored interval (default 15 seconds) and only explains the recommended camera setup.
5. The old initial pairing overlay hid all tabs when unpaired. The approved layout makes the unpaired Status page accessible and launches the connection instructions through Start Setup. Replaying setup preserves an existing pairing.
6. The original auto-highlights help does not mention every ride-state precondition. Its approved copy is kept; runtime guard changes require separate review.

## Help edits

Five topics: ride preparation, prevent standby, auto highlights, different widgets, GPS telemetry. Ride preparation contains only the three approved steps and a small lock-menu illustration. The standby article omits implementation details, QS commands and the old battery section, and uses the author's four-hour testing statement. Widget help includes the hidden-caption selection workaround. The new camera-menu illustration is a schematic, not a camera screenshot.

## Validation

See the CI test results and native render artifacts for the current commit. Robolectric tests exercise unchanged defaults/saved values, native controls in both appearances, stable debug layout, platform confirmation cancellation, all Help topics, and setup reopening without preference changes. These are Android software renders at a 480 × 755 app viewport (the Karoo's 480 × 800 display minus its 45-pixel status bar), not physical-camera or on-device Bluetooth tests.


### Verified result

- Android build and all nine tests passed for commit `66a99cc`: https://github.com/mtenkoppel/AeroCam/actions/runs/41616186775
- The defaults and all runtime camera, BLE, automation, and widget source files are unchanged from the baseline.
- Native renders were reviewed at 480 × 755 app pixels in dark and light. Review caught and fixed tab hit targets (now fixed at 48dp) and confirmed complete bottom-of-page rendering.
- Figma: https://www.figma.com/design/9lQyWc0V7x2CLGFpwwVym5/AeroCam?node-id=36-1656
- The Settings section contains 20 editable viewport/content references, 39 reusable reference components, and editable Roboto text. No flattened UI screenshot is used as a design layer.
- No installation on the user's Karoo, merge, or release was performed. Camera hardware/ride regression remains a device validation step.


## Follow-up: independent gradient triggers and localization

The user subsequently authorized exposing gradient triggers and explicitly requested uphill and downhill as independent options, each requiring both its own speed and gradient thresholds. The UI now includes both native switches and four numeric inputs. Downhill retains its existing off / 50 km/h / −3% behavior, preference key and speed limits (40–80 km/h). The new uphill option starts off / 20 km/h / +3%, with speed limits 1–80 km/h. Gradient ranges are +1…+30% uphill and −30…−1% downhill. Signed numeric input is enabled for downhill.

The runtime now reads both selected values for each direction. Independent state machines retain the old downhill hysteresis margins, suppress duplicate markers near thresholds, reset when disabled, and handle missing/non-finite sensor values. Low thresholds can re-arm at a stop or on flat ground. Camera commands and recording/ride guards are unchanged.

The ride-end bookmark inconsistency was explained to the user: its direct camera-command path checks ride bookmarks and recording but skips the auto-highlights master check. That behavior has not been changed by this follow-up.

All active Help pages, the camera setup guide, image accessibility text and new settings/setup UI strings are translated into the five additional supported languages. The supported-language resource keys now match English completely. English fallback remains available for unsupported languages. This resolves the earlier descent-switch and translation deferrals; it supersedes the original UI-only scope for the explicitly requested gradient behavior.
