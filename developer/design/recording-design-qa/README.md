# Recording button design QA — 2026-11-05

Native Android RemoteViews renders, not device screenshots. Source: `510ad02`.
[CI run](https://github.com/mtenkoppel/AeroCam/actions/runs/42000996025):
113 debug tests, 4 release privacy tests, and debug APK build passed.
Only the test capture code changed for this QA set; production alignment and
recording rules remain unchanged.

## Start here

| Review | Dark setting | Light setting |
|---|---|---|
| Centered body, all five sizes, manual / AUTO START / AUTO START + STOP | [Open board](dark-centered-sizes.png) | [Open board](light-centered-sizes.png) |
| Centered body, long clip / highlight feedback / pending stop | [Open board](dark-recording-states.png) | [Open board](light-recording-states.png) |
| Full Cam Control, three recording modes | [Open board](dark-cam-control.png) | [Open board](light-cam-control.png) |
| Current alignment behavior: LEFT / CENTER / RIGHT | [Open board](dark-alignment-bug.png) | [Open board](light-alignment-bug.png) |

The title is visible in every capture. Native source sizes are 238×113,
480×113, 480×154, 480×189 and 480×420. Captions consume 45 pixels at the test
300dpi density. Boards place original images at 1:1 scale; open the PNG directly
and zoom to inspect the smallest text. Individual PNGs use descriptive filenames.
Video + Photo reuses the Video recording renderer; these same button captures
apply to its active recording state. Surrounding ready-state/photo layouts are
not redesigned here.

## What to review

- AUTO START + STOP uses a plus and sits under the time.
- The small/half-width recording timer currently shrinks noticeably when the
  automation line is present. This is an explicit design-review point, not a
  claim that final on-bike readability has been established.
- Highlight feedback retains the existing implementation: compact fields can
  temporarily replace the timer, while larger fields add a feedback line.
- Pending commands retain the current spinner presentation and hide the status
  line temporarily. No pending-command policy was changed.
- No camera icon or storage metadata inside the recording button; Cam Control's
  independent status area retains storage.

## Confirmed released alignment mismatch

Checked tag sources v1.0 and v1.0.2 directly. Video timers/status and the Cam Control
recording group follow ViewConfig.alignment. The field title is fixed left.
The SDK defaults ViewConfig.alignment to RIGHT, which explains the earlier
right-aligned example. Its enum documents number/graphic alignment, not a separate
title preference. Our intended product behavior is centered button content;
field-title behavior needs coordination with issue #55. The comparison board
shows the current bug, not a shipped fix. AUTO labels themselves are only on
this preparation branch, not in the releases.

A normal-release alignment fix will be tracked separately. No merge, deployment,
automation logic change, or physical-device validation is part of these captures.
