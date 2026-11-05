# Cam Control layout proposal · #53

[Issue #53](https://github.com/mtenkoppel/AeroCam/issues/53) · Source baseline: `7bf90c5`.

Use a two-line status block: **battery + resolution** on the first line and
**storage** on the second. Keep the existing controls in their current positions.

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

Keep the existing 72 dp utility targets. Use 6 dp within button groups and
15 dp between the full-screen utility, capture and sleep groups, as the layout
already does. Align all status glyphs on a 14 dp box and use a consistent
4 dp glyph-to-value gap. Keep the values centered as a group, with a small
gap between battery and resolution. Tint status icons with the same
foreground as their values; the caption setting should not change status icons.
The current ride widgets use a fixed dark palette. The proposal previews a
light palette for review as well; scope any implementation change to Cam Control.

Use `DjiCameraState.videoResolution` and `videoResolutionLabel()` from the
existing status update path. Show the mapped label when it fits; for an
aspect-ratio variant, try its mapped base resolution (e.g. `4K 9:16` → `4K`)
before hiding the resolution group. Measure against the actual remaining
status width. Prioritize battery, storage and current tap targets; never add a
third line or reduce their sizes to fit. Half width has no side buttons and
may fit a longer resolution label than the full-width toolbar.

Hide resolution until the connected camera reports a known value for the
current session. Recompute on each state update and explicitly clear/hide the
group when reapplying RemoteViews. The BLE client caches resolution; a connected
flag alone does not establish that a cached value belongs to this session.
Verify the fresh-status path before deciding whether a scoped freshness flag
is needed. Keep existing offline messages and half-width battery/storage
placeholders.

For implementation review, capture before and after on Karoo 3 in light and
dark, covering all five presets, captions on/off, recording and Highlight,
Sleep confirmation, long/localized values, and a resolution change followed
by disconnect/reconnect. The branch uses
[Karoo SDK 1.1.9](https://github.com/hammerheadnav/karoo-ext/releases/tag/1.1.9)
(KOS 1.634.2440 or later). No device or firmware was tested for this proposal.
