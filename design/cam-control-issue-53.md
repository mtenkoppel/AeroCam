# Cam Control layout proposal · #53

[Issue #53](https://github.com/mtenkoppel/AeroCam/issues/53) · Source baseline: `7bf90c5`.

Put battery and storage side by side. Give video settings a dedicated second
line with a filmstrip icon. On full screen, span the field with the status block
above the Keep Awake and Settings buttons. Use an even gap between the four
button rows and remove the dedicated spacer rows.

| Gallery preset | Field (px) | Grid | Existing connected layout |
| --- | ---: | ---: | --- |
| Full screen | 480 × 420 | 60 × 60 | Keep Awake, status and Settings; Video, Photo and Sleep below. Highlight appears while recording. |
| Large | 480 × 189 | 60 × 30 | Keep Awake, status, Settings |
| Medium | 480 × 154 | 60 × 20 | Keep Awake, status, Settings |
| Small | 480 × 113 | 60 × 15 | Keep Awake, status, Settings |
| Small + half | 238 × 113 | 30 × 15 | Status only |

These are the five presets in [WidgetGalleryActivity](../app/src/main/kotlin/com/karoopoc/WidgetGalleryActivity.kt) and
[the native capture fixture](../app/src/androidTest/kotlin/com/karoopoc/widget/WidgetRenderingInstrumentation.kt).
There is no separate X-Large preset. The shared classifier enters Full at
45 rows or 280 px high; Cam Control overrides that for narrow fields
(at most 30 columns or under 360 px wide). The caption, when shown, consumes
22 dp before the renderer receives its available height.

Keep the existing 72 dp utility targets in compact fields. Use 6 dp gaps between
the full-screen controls. Align the status glyphs on a shared box and use a
consistent gap between each glyph and value. Tint status icons with the same
foreground as their values; the caption setting should not change status icons.
The current ride widgets use a fixed dark palette. The proposal previews a
light palette for review as well; scope any implementation change to Cam Control.

Use `DjiCameraState.videoResolution` and `videoFps` from the existing status
update path. Decode the aspect ratio from the resolution index. Try
`1080p · 30 fps · 16:9`, then `1080p/30 · 16:9`, `1080p · 30 fps`,
`1080p/30`, and `1080p`
as room runs out. Measure against the actual status width before hiding the
video line. The half-width field has no side buttons and can have more space
for the video label than the compact full-width strip.

The live Karoo readout settled at Video mode, resolution index `10`, FPS
index `3`, and EIS index `1`. These map to 1080p, 30 fps and RockSteady.
DJI specifies 16:9 for 1080p on the Action 4. The protocol also provides
separate resolution indices for documented 4:3 and 9:16 variants, a photo
aspect-ratio field, camera mode, and remaining recording time. The FOV byte
is reserved. AeroCam decodes EIS but does not expose it in widget state.
See the [DJI status protocol](https://github.com/dji-sdk/Osmo-GPS-Controller-Demo/blob/main/docs/protocol_data_segment.md#camera-status-push-1d02)
and [Osmo Action 4 specs](https://www.dji.com/osmo-action-4/specs).

Hide video settings in Photo mode and until the camera reports a known video
resolution and a valid video FPS index for the current session. The live Photo
payload reported `16/0/0` for resolution, FPS and EIS. The first Video-mode
payloads still carried the same tuple before the valid video profile arrived.
After the camera returned to Photo mode, its packets continued to carry
`10/3/1`, matching the last verified video profile. These fields can carry
previous-mode values in either direction, so a Photo packet alone cannot
verify the current video profile.
Recompute on each state update and explicitly clear or hide the group when
reapplying RemoteViews. Verify the fresh-status path before deciding whether a
scoped freshness flag is needed. Keep existing offline messages and half-width
battery and storage placeholders.

For implementation review, capture before and after on Karoo 3 in light and
dark, covering all five presets, captions on/off, recording and Highlight,
Sleep confirmation, long/localized values, and a resolution change followed
by disconnect/reconnect. The branch uses
[Karoo SDK 1.1.9](https://github.com/hammerheadnav/karoo-ext/releases/tag/1.1.9)
(KOS 1.634.2440 or later). The live BLE status was checked through Karoo.
Layout checks on the device are pending.
