# Video + Photo field

The combined field composes the existing video (left) and photo (right) RemoteViews
while the camera is idle. Once camera state reports recording, it returns the
standalone video renderer directly. Stopping restores both controls only after
camera state confirms the change. No BLE, command queue, connection lifecycle,
keep-awake, or widget controller logic is changed.

## Behavior contract

- Child renderers retain their PendingIntents, caption-dependent icon sizing,
  themes, pressed selectors (yellow outline), reconnect prompts, spinners and
  photo success feedback. Only the outer background/padding is owned by the
  combined container. Each child receives its actual available slot dimensions.
- Pending start stays split. Pending stop stays in the recording layout.
- Recording retains existing timer, storage subline, highlight button and brief
  feedback. Compact sizes omit storage. Half-width recording omits the highlight
  button, exactly as the video field does.
- Repeated presses, timeouts, photo/recording guards and camera acknowledgements
  remain the responsibility of the existing controller and camera client.
- Existing field IDs are unchanged. The new ID is `dji-video-photo`, registered
  in the manifest, extension types and advertised device data types.

## Verification

`testDebugUnitTest` and `assembleDebug` run in CI. The signed workflow produces a
certificate-verified release artifact; this does not publish or install a release.

The optional **Native widget verification build** uses `-PwidgetCapture=true` to
build `com.karoopoc.capture`, isolated from the rider installation. Install its
app and test APK, then run:

```sh
adb shell am instrument -w com.karoopoc.capture.test/com.karoopoc.widget.WidgetRenderingInstrumentation
```

The runner refuses the real app package. It compares native pixel output against
the original renderers across five sizes, two themes, captions on/off and thirteen
states, including pressed surfaces. It checks click bindings and repeated
RemoteViews reapplication, and saves example PNGs under the capture app's external
files `widget-verification` directory. It does not send camera commands. Remove
only the two `.capture` helper packages after collecting the results.

## Compatibility review — 2026-11-03

Official API docs: https://hammerheadnav.github.io/karoo-ext/
Release requirements: https://github.com/hammerheadnav/karoo-ext/releases/tag/1.1.9

The existing SDK remains 1.1.9 (KOS 1.634.2440+). This adds a graphical DataType
using the existing ViewConfig/RemoteViews/UpdateGraphicConfig integration; no SDK
or toolchain upgrade is involved. Validation results and device versions are
recorded below when available. The HTML dummy illustrates the resulting behavior;
it is not the implementation or evidence of BLE delivery.

## Validation — 2026-11-03

Implementation and native harness at `da19b62`:

- CI run 41610003493: unit tests and debug build passed.
- Signed test build 41609999275: release build and signing verification passed.
- Native verification build 41609999336: passed; run on the connected Karoo k24,
  Android 12/API 32, 480×800 at 300 dpi.
- **1,060 native pixel comparisons passed**, including pressed states and layout
  after RemoteViews reapplication. Spinner visibility and click bindings passed.
  130 example images were collected; idle, success and recording images reviewed.
- Both isolated helper packages were removed. The rider installation was unchanged.

The public KOS version was not identified (launcher version 4.217.1-ad98202524
is not the KOS version). These are rendering and build checks, not a live camera
or ride regression. The HTML preview uses simulated camera response timing;
actual responses continue to use the existing controller.
