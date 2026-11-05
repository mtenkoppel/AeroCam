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
the full-screen controls. Use a battery fill beside its percentage, an SD card,
and a filmstrip. Keep the battery icon neutral at 20% or above and red below
20%. Give the icons the same box and stroke weight, and use the
same text size and weight for every value. Grey out the Photo button while
recording and use the regular camera glyph. Keep the active Prevent standby
button on a neutral background and use its glyph to indicate state.
The caption setting should not change status icons.
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

Show the filmstrip and **Refresh** when the camera is connected and no valid
video profile is available. On the small half field, move the battery and
storage row up, leave more space between the two rows, and draw a smaller
SD-card glyph within its existing slot. Only switch
from Photo to Video after the user presses the refresh control. Leave the
camera in Video and show **Switching to Video…** while waiting for a fresh
status with valid parameters before
replacing the button with the readout. If already in Video, wait for or
request a status update without switching modes. Restore the refresh button
after failure or timeout. Offer it again after reconnecting if needed.
DJI documents the resolution and photo aspect ratio as status fields, with no
setter in the public remote protocol. The readout has no edit control.
Starting a recording already requests Video mode, so use its incoming status
to update the same label. Never queue a refresh mode change during recording.

The dedicated BLE command `0x1D/0x04` was tested against the connected camera
through AeroCam's existing command queue. The camera moved Photo → Video
(`0x01`) → Photo (`0x05`) and reported live view with a zero recording timer
throughout. The refresh action should send the explicit Video target instead
of the generic toggle. See [DJI Mode Switch](https://github.com/dji-sdk/Osmo-GPS-Controller-Demo/blob/main/docs/protocol_data_segment.md#mode-switch-1d04).

The first live Photo payload reported `16/0/0` for resolution, FPS and EIS.
The first Video-mode
payloads still carried the same tuple before the valid video profile arrived.
After the camera returned to Photo mode, its packets continued to carry
`10/3/1`, matching the last verified video profile. These fields can carry
previous-mode values in either direction, so a Photo packet alone cannot
verify the current video profile.
Photo mode reported 8,115 estimated shots remaining. DJI also documents
photo size, aspect ratio, burst count and countdown fields. The installed
app does not expose the live photo aspect-ratio byte or the countdown
fields. Its shared size and burst bytes retained video settings in this test.
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
