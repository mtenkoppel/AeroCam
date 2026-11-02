# First connection and onboarding

Status: interactive specification draft v3, 2026-11-02. Branch: codex/onboarding.
No Android implementation or deployment is part of this prototype revision.

## Story

As a rider pairing AeroCam, I want optional guided camera setup and configurable
ride triggers so I can control my DJI camera with minimal preparation each ride.

## Flow

1. **Connect:** larger existing logo above AeroCam, Search, and “Steps to Connect
   Camera”. Search simulates successful pairing in the mock only.
2. **Connection success:** prominent green checkmark, approximately 168 × 168
   pixels on the 480 × 800 canvas, followed by “Camera connected”. Continue.
3. **Welcome:** retain “Less fiddling. More riding.” Say “Take a moment to set up
   your DJI camera for a great in-ride experience.” No Back or connected-status
   label here. Footer ghost Skip and primary Start setup.
4. **KeepAwake:** a single inset box with a large coffee icon beside the KeepAwake
   heading. Recommend enabling the feature; explain AeroCam prevents sleep and
   cannot reach the camera once off. State battery cost. Retain the owner's
   observation: about four hours standby using a 1,770 mAh battery, actual life
   varies. Remove the “Good news” sentence. Footer Skip / Enable KeepAwake.
5. **Camera settings:** headline “Now let’s make some settings on your DJI cam
   together.” Auto Power Off: five minutes; optional mute; enable Single Screen
   Preview (Scrn Off when Locked), with its display-off explanation and menu path.
   Under the timeout, “I don’t want 5 minutes” opens the alternate-time guide.
   Footer Skip / Next. Next confirms settings and enables KeepAwake.
6. **How it works:** numbered dots: (1) Turn on your camera; AeroCam connects
   automatically (Karoo must be on). (2) Lock the camera; display turns off.
   Include the schematic control menu highlighting the second icon in the bottom
   row. (3) Ride! If KeepAwake was skipped, show the off-state behavior instead.
7. **Auto-highlights:** merge the previous introduction and configuration screens.
   Explain: “AeroCam can add highlights or even automatically start recording:”
   Show labeled options with one-line explanations and editable thresholds.
   Auto-start recording remains a separate, initially off option. Footer Skip /
   Next. Require at least one selected trigger for Next; Skip turns this feature
   and its trigger flags off.
8. **Before your next ride:** introduce small-to-large AeroCam control widgets,
   show a simple sample widget, and explain automatic GPS streaming into supported
   video recordings. A connected camera and a GPS location are required. Finish
   setup leads to a minimal completion state.

From welcome onward, remove the connected label and progress indicator. Retain
Back after welcome, with a quiet “Your DJI camera” header. Brief linear sliding
transitions remain and respect reduced-motion preferences.

## Alternate standby setting

Guide the rider to Settings → Auto Power Off on the DJI camera. The prototype
supports the previously discussed 1, 2 and 5 minute choices, not a claim to list
all camera firmware options. Require the rider to choose the same value here.

Set `keepAwakeIntervalSeconds = cameraStandbySeconds - 30` only upon camera-setup
confirmation. Thus 1 minute → 30 seconds, 2 minutes → 90 seconds, 5 → 270 seconds.
The preview displays the resulting interval. Back without saving leaves the old
choice intact. KeepAwake stays off throughout incomplete setup. Do not offer
30-second standby with this formula because it would produce a zero interval.
Only prototype state is changed today, not the app's setting.

## Trigger behavior verified from source

Read DjiSettings.kt and DjiKarooIntegration.kt on 2026-11-02.

| Option | Explanation | Draft threshold | Existing source default / bounds |
| --- | --- | --- | --- |
| Lap marker | Whenever you record a lap | None | Off |
| Peak power | When power crosses the threshold | 350 W, explicitly requested by owner | 500 W; 300–1000 W |
| Max speed | When speed crosses the threshold | 50 km/h | 50; 30–80 km/h |
| Climb summit | When route distance reaches the end of a route climb | None | Off; needs route climb data |
| Peak heart rate | When heart rate crosses the threshold | 165 bpm | 165; 120–220 bpm |
| High-speed descent | Speed threshold reached with grade at or below −3% | 50 km/h | 50; 40–80 km/h |
| Start/end bookmarks | Ride boundary markers while camera is already recording | None | On |

All trigger defaults follow source except the explicit requested power change.
The source code itself still uses 500 W; the 350 W change is only in the draft.

Enabling a threshold-based trigger opens a focused numeric editor with the
default prefilled. Save threshold confirms the value and enables that trigger.
Back leaves a newly selected trigger off. An enabled row displays its value and
allows reopening the editor. Values must be whole numbers within source bounds.
This avoids squeezing seven controls plus numeric fields into the Karoo screen.

Source semantics: power, speed and heart-rate triggers fire on threshold crossing
with hysteresis, not continuously while above the threshold. Auto-start recording
starts a recording when a trigger occurs while idle; it does not also add a marker
on that same event. Start bookmark is sent once when the camera is recording and
the ride is actively recording, including when camera recording starts mid-ride.
End bookmark currently occurs specifically on active RideState.Recording → Idle
while camera recording continues; ending from Paused is not covered by that path.
Bookmarks do not automatically start a recording.

Implementation note found in review: the existing end-bookmark path checks
rideBookmarks but not the master autoHighlightsEnabled setting. The onboarding
Skip action should clear both flags. Any broader consistency fix is separate from
this visual specification; do not claim it has been changed.

## State, appearance and scope

- First pairing only; not a prompt on every reconnect.
- Welcome Skip bypasses optional setup. Feature Skip affects that feature only.
- No QS while camera setup is incomplete. Next commits the chosen interval and
  enable flag together. The camera setting and lock state are user-confirmed,
  not readable by AeroCam.
- Real implementation must persist progress, resume safely after disconnect or
  process restart, and define replay/edit semantics. These interruptions are not
  simulated in this draft.
- Use AeroCam spelling consistently. “ErroCam” is treated as dictation.
- 480 × 800 physical Karoo canvas, status bar included, proportional narrow-screen
  preview. Theme follows the conversation with optional appearance preview.
- Widget illustration uses recognizable camera/video/coffee controls; it is a
  non-operating sample, not a new widget design committed to the Android app.
- Final review can adjust copy and selected defaults before application coding.
