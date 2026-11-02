# AeroCam project review — 2026-11-02

Scope: source review of 68bc3d5 plus the infrastructure version bump; build/test/signing checks; Karoo install/launch/screenshots. This is not a long-duration BLE or ride regression test. Private research was consulted but is not reproduced here.

## Overall assessment

The architecture is understandable and suitable for focused iteration. Protocol encoding (DjiProtocol), persistent settings (DjiSettings), camera service/controller, Karoo integration, widget controller/rendering, help, and theme already have distinct responsibilities. The main maintenance pressure is concentration in DjiOsmoBleClient (~1,791 lines) and MainActivity (~1,022 lines), not a need to replace the framework.

The Settings / Status / Help navigation is logical. Theme palettes, row/card helpers, resource strings, and the shared widget renderer provide useful reuse. Extract screen sections and shared UI primitives gradually when changing them. Keep Bluetooth state and scheduling out of those UI components. The widget gallery reuses the renderer, but is not exported and has no obvious launcher from MainActivity; improve debug-only access if preview screenshots become frequent.

## Prioritized findings

1. **High: stale callbacks and mixed threading in BLE lifecycle.** `DjiOsmoBleClient.gattCallback` mutates connection state and characteristics without consistently rejecting callbacks from an old GATT instance. In `onConnectionStateChange`, the identity comparison only protects clearing the GATT reference, after other state has already been reset. `onCharacteristicWrite` completes the current pending write without checking the connection generation. An old callback arriving after reconnect could disrupt the new session. Serialize state changes and validate the connection/session before handling callbacks. Add delayed-old-callback tests.
2. **High: no deadline for response-bearing BLE writes.** `drainWriteQueue` marks a write active and waits for `onCharacteristicWrite`; only no-response writes have a completion timer. If the callback is lost while notifications keep the link fresh, the liveness watchdog need not recover the blocked operation queue. Add a bounded write deadline tied to the pending write/session, with focused timeout/reconnect tests.
3. **Medium: keep-awake is difficult to verify in isolation.** Guards, scheduling, coalescing, priorities, liveness checks, and intentional-sleep cancellation are present. However, `keepAwakeActive` expresses eligibility rather than confirmed pulse success, and the QS counter increments before write completion. Extract a clock/scheduler-driven policy and distinguish scheduled, sent, and failed pulse evidence. Test recording start/stop, disabling, disconnect, stale link, and write failure. Do not describe a build pass as a battery/runtime test.
4. **Medium: integration cleanup depends on its owner.** `DjiKarooIntegration.start` does not retain consumer IDs and `stop` only cancels camera observation. The current extension owner subsequently disconnects KarooSystemService, so this is not proof of a current leak. Explicit consumer ownership and idempotent start/stop would make the component reusable and safer to isolate.
5. **Medium: build compatibility debt.** AGP 8.2 supports compile SDK through 34, but AeroCam compiles with 35 and suppresses the unsupported-SDK warning in gradle.properties. A successful build does not make that an officially supported pairing. Upgrade AGP/Gradle/Kotlin together in a dedicated PR; remove the suppression after validation.

Only two unit tests currently exist (widget size classification and video label mappings). They passed, but do not cover the connection lifecycle, command queue, keep-awake, protocol parsing, or automated highlights. Prioritize deterministic lifecycle tests before a broad refactor.

## Keep-awake assessment

The mechanism keeps the DJI camera available, rather than keeping the Karoo display on. A foreground connected-device service owns BLE work; it requests Karoo Bluetooth and releases the same resource on teardown. QS pulses are eligible only while enabled, protocol-connected, and not recording. Pending pulses are coalesced and guarded again before execution. Disable/sleep and disconnect paths have cancellation logic. The default interval is 15 seconds, clamped to 10–300 seconds.

This is a sensible foundation, with the callback/timeout/test gaps above. An allowed interval can exceed the camera's configured auto-power-off timeout, so any future interval UI should explain that relationship. No claim of compatibility with every camera firmware, guaranteed wake from full power-off, or guaranteed runtime follows from this review. Re-pairing is required after the authorized signing migration before further physical camera checks.

## Dependency status

Checked against official sources on 2026-11-02; newest does not automatically mean safest to adopt.

| Component | Repository | Current upstream observed | Assessment |
| --- | --- | --- | --- |
| Karoo Extensions | 1.1.9 | 1.1.9 | Current; release notes require KOS 1.634.2440+ for its capabilities |
| Kotlin | 2.0.0 | 2.4.21 | Behind; coordinate compiler/plugin migration |
| Android Gradle Plugin | 8.2.2 | 9.5.0 | Behind; current compile SDK 35 pairing is unsupported |
| Gradle wrapper | 8.6 | 9.8.2 | Behind; select version against AGP compatibility, not independently |
| Java | 17 target/toolchain | JDK 17 remains supported by AGP 9.5 | No reason to change the app's Java target solely for freshness |
| JUnit 4 | 4.13.2 | 4.13.2 on JUnit 4 line | Current within its line; newer JUnit is a migration, not mandatory cleanup |
| GitHub actions | Mostly v4 | checkout 7.0.1, setup-java 6.1.0, upload-artifact 7.0.2, gradle/actions 6.4.1 | Behind; existing Dependabot PRs cover several updates |

The app uses Android Views and RemoteViews, not Compose/Hilt. Hammerhead's sample uses additional frameworks but explicitly does not require them. Transitive AndroidX/coroutines dependencies come through karoo-ext; this review is not a complete transitive vulnerability audit.

Connected device: k24, Android 12 / API 32; launcher/settings package version 4.217.1-ad98202524. This package version is not the public KOS number. Installation and launch were checked; confirm the KOS version in device Settings before claiming the full SDK compatibility floor is met. Karoo 2 was not tested.

Official references:
- https://github.com/hammerheadnav/karoo-ext/releases/tag/1.1.9
- https://hammerheadnav.github.io/karoo-ext/
- https://hammerheadnav.github.io/karoo-ext/karoo-ext/io.hammerhead.karooext.models/-request-bluetooth/index.html
- https://github.com/hammerheadnav/karoo-ext#sample-app
- https://developer.android.com/build/releases/agp-8-2-0-release-notes
- https://developer.android.com/build/releases/agp-9-5-0-release-notes
- https://kotlinlang.org/docs/releases.html
- https://docs.gradle.org/current/release-notes.html

## Next focused tickets

- Reject stale GATT callbacks and serialize lifecycle state transitions.
- Add response-write deadlines and queue recovery tests.
- Extract/test keep-awake policy and report pulse outcome accurately.
- Upgrade the Android build toolchain as a compatible set.
- Extract reusable main-screen sections without changing behavior.
- Make Karoo consumer registration and teardown explicit.

Keep release signing in GitHub secrets. A successful signed-build artifact is for testing; publishing a release is a separate operation. An independent secure signing-key/password recovery arrangement remains desirable; this review did not recover the password locally.
