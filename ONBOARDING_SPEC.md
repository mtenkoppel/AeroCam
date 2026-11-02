# Camera onboarding

Implemented on `codex/onboarding`, 2026-11-02, from the owner-reviewed interactive prototype.

## Final accepted flow

1. Pair camera: existing search and security approval, compact AeroCam logo, sentence-case instructions. Search is real in the app.
2. Connection confirmed: large checkmark; explain automatic reconnection. Continue explicitly (no automatic dismissal).
3. Welcome: “Less fiddling. More riding.” Device line drawings. Start setup or Skip.
4. Recommend **Prevent standby**: explain five-minute camera timer even during an active connection; coffee icon; battery observation (~4 hours with 1,770 mAh, not a guarantee). Skip or enable.
5. Camera setup: auto power off five minutes (or matching one/two minute choice); optionally mute clicks; enable **Single Screen Preview (Scrn Off when Locked)**. Camera menu labels stay in their exact provided English form in all translations. Skip or confirm.
6. “Next time you go for a ride, you can just:” turn on camera, lock camera, control from Karoo. Illustrated lock location.
7. Auto-highlights: five opt-in triggers (lap, power, speed, route summit, heart rate). Numeric trigger enabling opens an editor; backing out leaves a newly selected trigger off. Auto-start recording is separate, available only when a trigger is selected. Turning every trigger off also turns auto-start off. Skip or Next.
8. Widget page: “Awesome.” Add widgets to the Karoo ride screen, sample controls, Next.
9. Final page: “Have fun with AeroCam.” Convenient camera control; GPS/speed/elevation streaming into supported recordings; DJI Mimo / GPStitch. Done returns to Settings.

The former standalone settings-revisit page was removed. The data explanation is now after the widget page. Prototype timestamps remain references, not visible production UI.

## Skips, persistence, and existing users

- Skip setup goes straight to Settings.
- Skip Prevent standby (or its camera settings) disables it via the existing controller, shows a short reminder about later setup and FAQ, then proceeds to triggers.
- Skip triggers disables their selected flags and auto-start, then goes directly to the widget page.
- First-time setup is prepared when the user presses Search; it only opens the pairing confirmation once the camera is actually connected. A persisted draft resumes thereafter even if the camera is temporarily disconnected.
- Draft page, navigation stack, timer, trigger values and unsaved numeric input survive process recreation. An incomplete camera setup never enables Prevent standby.
- The selected timer is applied before enabling through existing controller APIs. No BLE, reconnect, QS retry, recording guard, service, or Karoo SDK changes.
- Existing configured installations are not forced through onboarding. A Camera setup guide button in Settings and Help permits replay. Replay loads existing trigger values. Entering camera setup suspends Prevent standby until confirmation. Deferred trigger settings are preserved on replay.
- Completing clears the draft and stores a completion marker. Returning to Settings rebuilds the UI from current preferences.

## Settings changes

- Power default: 250 W; speed default: 40 km/h; heart rate default remains 165 bpm.
- All five onboarding triggers start off for a new installation. Selecting any enables auto-highlights at Next. New first-pairing setup disables legacy default-on ride bookmarks.
- Power/speed/heart-rate editors and settings reads accept positive integers without the old artificial ranges. Storage uses Android Int; invalid/overflow inputs cannot be saved. Existing valid saved thresholds are retained.
- Power feedback: >=300 W, >=350 W, >500 W. Speed feedback: >=50, >55, >60 km/h. Heart-rate input has no joke text.
- High-speed descent and start/end bookmarks are omitted pending owner review: issues [20](https://github.com/mtenkoppel/AeroCam/issues/20) and [21](https://github.com/mtenkoppel/AeroCam/issues/21). Their underlying trigger algorithms are unchanged.
- Timer choices map 60→30, 120→90, 300→270 seconds. No timer is inferred from the camera; the rider confirms it.
- The stream carries GPS position, movement and elevation; it does not carry heart rate or cycling power. Those claims must not appear in onboarding.

## Compatibility and validation

Official Karoo documentation checked 2026-11-02:
- https://hammerheadnav.github.io/karoo-ext/ (1.1.9)
- https://github.com/hammerheadnav/karoo-ext/releases (1.1.9 requires KOS 1.634.2440 or later)

SDK remains 1.1.9; compile SDK 35, target 34, minimum Android API 23. Android API and Karoo OS versions are distinct. No dependencies or manifest permissions added. The new Activity is private to AeroCam.

Onboarding strings are provided in English, German, Spanish, French, Italian and Portuguese. Theme uses DjiTheme. Layout uses scrollable content plus fixed footer actions, native Android back arrow, switches and numeric input. No new UI framework.

Automated checks: testDebugUnitTest / assembleDebug (CI) and release-signed build. Policy tests cover timer margin, input validation outside previous limits, invalid inputs, and feedback boundaries. Prototype interaction checks cover skip paths, gating and final navigation.

No Karoo was attached during implementation. On-device visual checks, keyboards, first pairing, process restart, theme/language layouts and camera confirmation remain to be performed. No installed firmware is claimed as tested for this change. Keep source rollback by retaining the branch base; do not uninstall or erase pairing data.
