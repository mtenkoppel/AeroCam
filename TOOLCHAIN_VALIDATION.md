# Toolchain validation — 2026-11-02

The owner approved adoption after the following cloud and device checks in
[PR #16](https://github.com/mtenkoppel/AeroCam/pull/16).

## Adopted combination

| Component | Before | Validated |
| --- | --- | --- |
| Android Gradle Plugin | 8.2.2 | 8.6.1 |
| Gradle | 8.6 | 8.7 |
| Kotlin compiler / standard library | 2.0.0 | 2.1.21 |

Java 17, compile SDK 35, target SDK 34, min SDK 23, Karoo Extensions 1.1.9,
release signing identity, and app source remain unchanged. The device build is
versionCode 16, `0.9.0-beta.3-toolchain.1`, from commit
`691c013ce1cfe2c354b37359e065fc96bcb59f32`. No public release was published.

## Cloud checks

- [CI](https://github.com/mtenkoppel/AeroCam/actions/runs/41415849104): debug build and both existing unit tests passed.
- [Signed build](https://github.com/mtenkoppel/AeroCam/actions/runs/41415840887): release build, tests, and established signing-certificate check passed.
- [Comparison](https://github.com/mtenkoppel/AeroCam/actions/runs/41414994009): the only resolved runtime artifact change was Kotlin's standard library. Other artifact hashes match, including Karoo SDK, AndroidX lifecycle, and coroutines. APK manifest/badging matched before the intentional device version bump.
- [Versioned comparison](https://github.com/mtenkoppel/AeroCam/actions/runs/41415840788) also passed.

Generated executable code, Kotlin metadata, build metadata, and optimization
profiles change with the toolchain. Build success alone does not prove runtime
stability. Existing unit tests cover widget layout/labels, not BLE lifecycle.

## Physical checks

Hardware: Karoo k24, Android 12 / API 32, DJI Osmo Action 4. Public KOS and camera
firmware versions were not recorded; Karoo 2 was not tested.

- Update installed over version 15 without uninstalling or clearing data.
- App launched, retained the camera pairing, and reconnected automatically.
- KarooSystem connected; camera foreground service and extension were active.
- Start/stop recording, photo, and highlight commands completed in logs. The
  owner confirmed recording worked and reported the highlight appeared to work.
- Owner powered the camera off/on; the app detected loss and automatically
  reconnected. Logs confirmed the ready state after reconnection.
- With the ride screen foregrounded, five keep-awake QS commands completed at
  15-second intervals over approximately 70 seconds before the setting was
  disabled. Camera auto-power-off was set to one minute. After further manual
  checking, the owner reported that everything seemed to work and approved merge.

This is a successful desk smoke test, not a new long-duration ride, exhaustive
failure-path test, or proof of compatibility with every firmware version.
Connection/lifecycle refactors and failure-path tests remain deferred in #15.

## Rollback and next version

- Preserve source branch `rollback/pre-toolchain-2026-11-02` and the original signed APK.
- `codex/toolchain-rollback-ready` at `84a4c0d` contains baseline code/build tools
  with versionCode 17 (`0.9.0-beta.3-rollback.1`) so it can replace version 16
  without uninstalling. Its [signed build](https://github.com/mtenkoppel/AeroCam/actions/runs/41415845078) passed; the APK was downloaded and its checksum verified locally.
- Do not merge the rollback branch into master. It is a recovery artifact only.
- Use `adb install -r` with the established release key; never clear app data as
  an automatic fallback. Check the installed version first. A version newer
  than 17 requires rebuilding rollback with a higher versionCode.
- Reserve 17 for rollback; the next normal device update should use at least 18.
- GitHub artifacts expire after 14 days. Local candidate, rollback, screenshots,
  and filtered observations are retained under the owner's
  `Documents/KarooPoC/toolchain-device-test` directory; do not commit private logs.

## Compatibility references

- [Android build plugin 8.6 requirements](https://developer.android.com/build/releases/agp-8-6-0-release-notes)
- [Kotlin / Gradle / AGP compatibility](https://kotlinlang.org/docs/gradle-configure-project.html)
- [Karoo SDK 1.1.9 release notes](https://github.com/hammerheadnav/karoo-ext/releases/tag/1.1.9): KOS 1.634.2440+.

Workflow-action upgrades remain a separate maintenance change. This adoption
selects a tested compatible combination rather than all available major versions.
