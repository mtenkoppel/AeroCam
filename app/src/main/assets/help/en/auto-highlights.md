# How auto highlights work

## What is a highlight?

A highlight marks an interesting moment inside a video recording. It helps you find that moment later without reviewing the complete recording.

AeroCam can add a highlight when something happens during your ride. The event that causes it is called a **trigger**.

## Ride start/end markers

This option is off for new installations. Updates preserve existing choices and the previous default for older installations. Enable both **Auto highlights** and **Ride start/end markers** to use it.

- A start marker is added only when the camera is already recording as the ride starts. Starting the camera later does not add a delayed ride-start marker.
- An end marker is added when an active or paused ride finishes, provided the camera is still recording. Karoo reports the same end state for saving and discarding a ride, so both can produce an end marker.
- Pausing and resuming do not add markers. Missed markers are not saved for later; reconnecting does not add a backdated marker.
- These markers never start a recording, even with Auto-start recording enabled, and bypass the automatic threshold cooldown.


## Available triggers

- **Lap marker:** A new lap starts.
- **Peak power marker:** Your power crosses the threshold you set.
- **Max speed marker:** Your speed crosses the threshold you set.
- **Climb summit marker:** Karoo reports the end of a climb.
- **Peak heart rate marker:** Your heart rate crosses the threshold you set.
- **Uphill marker:** Both your selected uphill speed and positive gradient thresholds are met.
- **Downhill marker:** Both your selected downhill speed and negative gradient thresholds are met.
- **Ride start/end markers:** Marks the ride start and end only when the camera is already recording. Pauses and resumes add no markers. Never starts a recording.

Only triggers that you enable in Settings are used.

## Uphill and downhill thresholds

Enable uphill, downhill, or both independently. Each direction has its own speed and gradient thresholds, and **both thresholds must be met**.

Use a **positive gradient for uphill** and a **negative gradient for downhill**. For example, uphill at **20 km/h** and **+5%** means at least 20 km/h on a 5% climb or steeper. Downhill at **60 km/h** and **−6%** means at least 60 km/h on a 6% descent or steeper.

The existing downhill values remain **50 km/h** and **−3%**, with the trigger off unless you enable it. The new uphill trigger also starts off, with **20 km/h** and **+3%** as initial values. Small fluctuations around a threshold do not create repeated highlights.

## When recording is active

When a trigger occurs during a recording, AeroCam sends a highlight command to the camera. The Video and Cam Control data fields briefly show which trigger was detected.

## Auto-start recording

Normally, Auto highlights only adds highlights while a recording is already active.

Enable **Auto-start recording** if you want a trigger to start a recording when the camera is idle.

The first trigger starts the recording but is **not** added as a highlight. Later triggers can add highlights to that recording.

This keeps the camera commands in a safe order and avoids sending Start recording and Add highlight at almost the same time.

## Choosing thresholds

Choose values that represent moments you genuinely want to find again. If a threshold is too easy to reach, the recording may collect too many highlights.

Start with a demanding value and adjust it after a few rides.
