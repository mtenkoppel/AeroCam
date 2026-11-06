# Automation design QA — build 51

The seven chat QA items are resolved by later decisions where they conflict.

1. Prevent standby and Automation have a `?` help action in their card header. The inline standby explanation moves to its existing help article; Recommended camera setup stays.
2. Automation information and standby help use the existing Help content renderer, including 24sp headings and 15sp body text/line spacing.
3. Catalog items group title, explanation and chevron in one tappable row, with 17sp bold titles, 15sp explanations and tighter spacing.
4. Trigger detail titles and explanations use the same Help renderer.
5. Add Trigger opens a bottom-up flow; trigger details drill down horizontally. Back discards the draft and returns to the catalog. Successful Add closes both pages and returns to Settings.
6. The earlier AUTO RECORDING wording proposal was retracted. The later icon decision supersedes the idle subline and spacing proposal.
7. Shared recording layout prefers approximately 30% larger timer/secondary text, fitting down for small slots. The final accepted icon design keeps existing production icon dimensions: an A inside the video icon in automatic recording mode, or inside the photo body (without its lens circle) in automatic photo mode. Recording shows no icon. AUTO REC is shown only for an owned automatic clip with automatic stopping enabled. Manual clips and stop-disabled clips retain centered timer-only layout. Existing REC STARTED, REC STOPPED and highlight feedback keep their priority, duration and fitting rules. Pending, disconnected, capturing, success and blocked photo states are preserved.

No automation algorithm, BLE lifecycle or SDK changes. SDK 1.1.9 remains unchanged (minimum KOS 1.634.2440, per official SDK release notes checked 2026-11-06).

Validation: CI unit tests and native Android rendering captures; signed build/device review evidence is stored outside source alongside the downloaded APK. No camera capture/ride validation is implied by screenshots.


## Follow-up browser review — build 52

- Shared outer frames now use 6px above/below when untitled; captioned fields retain 4px above and 8px below, keeping the same total height budget. Covers all four field types and both caption states in regression captures.
- Header help uses the official Google Material help_outline vector tinted with the existing theme accent. Automation uses the same A silhouette as its widget indicators.
- Compact card padding/header gaps and control rows; standby toggle reads “Enable prevent standby” (localized). Removed all three automation mode sublines.
- Catalog choices are shaded, whole-row tappable boxes. Trigger detail has one explanatory sentence per trigger; the generic second paragraph is removed.
- Numeric validation clears when the corrected value becomes valid. The earlier red exclamation mark in the gallery was a stale error left by an invalid-input test; invalid input remains validated, and the normal screenshot is now captured after clearing the corrected error.
