# AeroCam agent guide

- Read README.md, CONTRIBUTING.md, and the relevant GitHub issue before editing.
- Before changing Karoo integration, Bluetooth behavior, UI data fields, or dependencies, check the current official documentation: https://hammerheadnav.github.io/karoo-ext/ and https://github.com/hammerheadnav/karoo-ext/releases. Record SDK/KOS requirements and the device/firmware actually tested. Do not confuse Android API level with Karoo OS version.
- Prefer GitHub Actions for tests and signed APKs. Keep the Mac lightweight; use ADB for installation, logs, and screenshots. Never put tokens, keystores, passwords, pairing identifiers, or private Bluetooth research in Git or build artifacts.
- Keep changes focused. Preserve boundaries between protocol encoding, BLE transport/lifecycle, camera service/state, Karoo integration, settings, and UI. Extract small testable policies when touching large classes; avoid unrelated rewrites.
- Reuse DjiTheme, existing UI row/card helpers, string resources, and DjiWidgetRenderer. Preserve light/dark themes, six languages, and compact Karoo field sizes. Do not introduce Compose or another UI framework just for cleanup.
- Keep-awake prevents an already connected DJI camera from powering off; it does not wake a powered-off camera. Preserve user-command priority, recording guards, cancellation, reconnect behavior, and battery-conscious scheduling. Follow CONTRIBUTING.md's hardware regression cases for lifecycle changes.
- Run testDebugUnitTest and assembleDebug for code changes; verify signed release builds before device deployment. Add meaningful tests for changed protocol/scheduling/state behavior. Report physical-device checks separately from automated tests.
- Deploy only APKs signed with the established release certificate. Use adb install -r to preserve data; never uninstall or clear app data without explicit user authorization. Increase versionCode for new device updates.
- For issue work: read the issue and comments, define observable acceptance criteria, use a focused branch, and link the PR with Closes #N. Include checks and screenshots when relevant. Work begins when the user assigns an issue/link in chat; do not claim automatic issue monitoring is configured.
- Build artifacts are not public releases. Publishing a release or changing release signing identity requires an explicit request.
