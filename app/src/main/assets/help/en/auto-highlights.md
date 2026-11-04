# How auto highlights work

## What is a highlight?

A highlight marks an interesting moment inside a video recording. It helps you find that moment later without reviewing the complete recording.

AeroCam can add a highlight when something happens during your ride. The event that causes it is called a **trigger**.

## Available triggers

- **Lap marker:** A new lap starts.
- **Peak power marker:** Your power crosses the threshold you set.
- **Max speed marker:** Your speed crosses the threshold you set.
- **Climb summit marker:** Karoo reports the end of a climb.
- **Peak heart rate marker:** Your heart rate crosses the threshold you set.
- **Uphill marker:** Triggers at the selected uphill gradient, with an optional minimum speed.
- **Downhill marker:** Triggers at the selected downhill gradient, with an optional minimum speed.
- **Ride start/end markers:** Marks the ride start and end only when the camera is already recording. Pauses and resumes add no markers. Never starts a recording.

Only triggers that you enable in Settings are used.

## Uphill and downhill thresholds

Enable uphill, downhill, or both independently. Use a positive gradient for uphill and a negative gradient for downhill. Turn off **Also require speed** to trigger on gradient alone. Turn it on to require both gradient and speed.

For example, use **+10%** with speed off to mark a steep climb at any speed. Use **+5%** and **30 km/h** to mark a fast climb. The same choice applies to descents, using a negative gradient. Small fluctuations around a threshold do not create repeated highlights.

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
