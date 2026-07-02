# How Keep awake works

## Why it exists

The DJI Osmo Action cannot be reliably woken by AeroCam after it has fully powered off.

**Keep awake** works around this limitation. It regularly sends a QS button press to reset the camera's automatic power-off timer. The camera stays connected and ready, even when its displays are off.

> **Battery:** Keep awake uses much more battery than true standby.

## Set up the camera

Keep awake works best if you configure the camera in a particular way:

1. Set **Single Screen Preview** to **Screen off when locked**. This lets the displays turn off while the camera remains available.
2. Set **Auto Power Off** to at least **2 minutes**.
3. Connect the camera to the Karoo, then lock the camera screen. This turns off the displays and saves some energy.
4. Turn off camera sounds if you do not want to hear a tick each time AeroCam sends the QS command.

Menu names may differ slightly between camera firmware versions.

## During a ride

Turn on Keep awake from Cam Control. AeroCam sends a QS command immediately and repeats it using the interval set in AeroCam.

If the connection is lost, AeroCam resumes Keep awake after the camera reconnects. It waits briefly after connecting so the camera has time to become ready.

Keep awake pauses while video is recording. During a recording, the same QS command creates a highlight. Pausing Keep awake prevents unwanted highlights. It resumes when recording stops.

## Battery use

In one test, the camera dropped from **100% to 64% in about 1 hour and 56 minutes**. This is only a practical indication: temperature, recording, screen use and battery condition all affect the result.

Use Keep awake when quick camera access matters. Turn it off when you no longer need the camera to stay ready.

> **Important:** Keep awake prevents power-off. It cannot wake a camera that is already fully powered off.
