# QS acknowledgement experiment

Status: Phase A completed 2026-11-02; Phases B and C pending.

## Decision this experiment supports

Can AeroCam use a long regular Keep awake interval, with short retries only when
its QS command has no matching successful DJI response? Do not implement retries
or change the production schedule until this first measurement is complete.

Duplicate QS presses are acceptable to the owner for the usual locked-camera,
idle use case. Preserve the recording guard: QS during recording can add a
highlight. Screen lock is a manual observation, not something AeroCam can verify.

## Evidence and hypothesis

DJI documents Key Reporting (command set 0x00, command ID 0x11), including a
one-byte response: 0x00 means command executed successfully; 0x01 parsing error,
0x02 execution failure, and 0xFF undefined error.

Source: https://github.com/dji-sdk/Osmo-GPS-Controller-Demo/blob/main/docs/protocol_data_segment.md#key-reporting-0011

AeroCam already logs outgoing frames, Android write results, incoming command
IDs/sequence numbers/response flags, and key-report return codes. A logged
Android write success alone is not a DJI command result. A DJI success response
is not a direct readout of the standby timer.

Hypothesis: each idle QS request produces a DJI success response whose sequence
number matches the request, usually well within the proposed 10-second timeout.
Verify sequence matching rather than assuming it from arrival order.

## Preparation

- Record app commit/version, camera model/firmware, Karoo model/KOS version and
  Android version. Do not substitute Android version for KOS version.
- Camera Auto Power Off: five minutes. Sounds muted; screen-off-when-locked
  enabled. Camera idle, manually locked, and physically observed dark.
- Save the existing Keep awake setting and interval so they can be restored.
- Connect USB and start a timestamped local log capture before enabling Keep
  awake. Use the existing scripts/logcat.sh, or capture the app process with ADB.
  Do not clear existing logs. Record manual actions with wall-clock timestamps.
- Keep raw logs local/private: they may contain pairing identifiers or GPS data.
  Publish only the sanitized measurement table below.
- Do not press record, highlight, photo, or change camera modes during the main
  sample; those actions can share the key-report command ID.

## Phase A: match responses (about four minutes)

1. With Keep awake off, connect and lock the camera. Capture 20 seconds of idle
   traffic to identify unsolicited events.
2. Enable Keep awake. With the current default 15-second interval, capture ten
   consecutive QS transmissions. Record the actual interval; do not assume the
   device has the default saved. Existing startup pulses may be immediate.
3. For each QS TX, decode the frame sequence with DjiProtocol (the current frame
   sequence is the little-endian uint16 at frame offsets 8 and 9). Verify its
   command is 0x0011 and payload is the QS short-press event.
4. Find RX frames with command 0x0011, response flag set, and the same sequence
   within the same connection session. Decode the one-byte return code; measure
   latency from actual TX, not scheduling. Record missing, late, duplicate,
   mismatched, malformed and nonzero responses separately.
5. Disable Keep awake and finish the capture. Do not treat battery/status/GPS
   traffic or an Android write callback as a matching DJI acknowledgement.

| Session | QS TX time | TX sequence | Android write result | DJI RX time | RX sequence / response flag | Return code | Latency ms | Physical display / notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Pending | | | | | | | | |

Outcome: report matching success count out of ten, missing/nonzero counts, and
minimum/median/maximum latency. Ten samples establish feasibility, not a failure
rate or reliability guarantee. If frames cannot be matched, stop and investigate
logging/protocol semantics; do not build an acknowledgement-based timer yet.

## Phase B: demonstrate timer reset separately (about ten minutes)

Only proceed after Phase A shows usable acknowledgements.

1. Reconnect with Keep awake off, lock the screen promptly, then leave the camera
   untouched. Record connection-ready and the last physical interaction time.
2. About four minutes after the last interaction, enable Keep awake to trigger
   one QS. Capture its matching success response, then disable Keep awake before
   the next recurring pulse. Verify in the log that exactly one QS was sent.
3. Leave the camera untouched and connected until sleep. Record both physical
   power-off and any reported sleep transition; delayed BLE disconnection is not
   the physical power-off timestamp.
4. If shutdown moves to approximately five minutes after that QS, this supports
   timer reset by an acknowledged QS. Extra QS commands, physical interaction or
   reconnection invalidate this timing trial; repeat it cleanly.

## Phase C: future retry validation (requires a separate implementation)

Do not simulate response loss by disconnecting: that tests reconnect handling,
not a lost acknowledgement on an otherwise healthy link.

Use a test-only response-handler seam to ignore one matching acknowledgement,
while retaining the raw trace. Verify one retry after ten seconds, with a fresh
sequence number, and that its success ends retries. Exercise late responses,
nonzero return codes, recording starting during the wait, disconnect, and Keep
awake being disabled. Cancel pending retries in those cases where QS is no
longer permitted. Establish a finite retry limit and never leave an unlimited
retry loop running. A missing Android write callback is a separate queue-stall
case; scheduling another command does not itself unblock that queue.

Candidate timing for later hardware validation: regular interval 240 seconds,
response timeout 10 seconds, at most two retries. These are proposals, not
current behavior. Schedule the next regular interval from confirmed success;
keep pending requests scoped to their connection session. A nonzero response is
an explicit failure, not success; determine which errors merit retry.

## Completion and scope

Record conclusions in the private standby-behaviour research document, with a
sanitized summary here if appropriate. Restore the user's Keep awake preference.
No automatic retries, interval change, lifecycle refactor or firmware change is
part of writing this experiment. Removing the temporary Settings disconnect /
reconnect buttons does not require changing BLE connection handling.

## Phase A results — 2026-11-02

Tested release-signed version 20 (`2905ff2`), Karoo k24 / Android 12
(build incremental `eng.ubuntu.20261014.145316`; KOS marketing version not
recorded), DJI ACTION4 reporting `SDK-v1.1 DEBUG AC203-03.04.80.15`.
Owner confirmed idle, screen locked, Auto Power Off five minutes.

Collected 12 QS requests at the existing 15-second interval, with
12 matching responses. All responses were command 0x0011,
response flag set, identical request sequence and return code 0x00.
Latency: 87–120 ms; median 106.5 ms.
No missing, nonzero or duplicate responses in this sample.

| QS TX time (device log clock) | Sequence | DJI return code | Latency ms |
| --- | --- | --- | --- |
| 13:59:48.953000 | 4 | 0x00 | 111 |
| 14:00:03.941000 | 5 | 0x00 | 111 |
| 14:00:18.930000 | 6 | 0x00 | 105 |
| 14:00:33.926000 | 7 | 0x00 | 95 |
| 14:00:48.929000 | 8 | 0x00 | 118 |
| 14:01:03.933000 | 9 | 0x00 | 100 |
| 14:01:18.935000 | 10 | 0x00 | 87 |
| 14:01:33.939000 | 11 | 0x00 | 112 |
| 14:01:48.941000 | 12 | 0x00 | 93 |
| 14:02:03.943000 | 13 | 0x00 | 120 |
| 14:02:18.942000 | 14 | 0x00 | 108 |
| 14:02:33.945000 | 15 | 0x00 | 90 |

Keep awake was restored to off after capture; saved interval remains 15 seconds.
The temporary connection buttons are absent from the installed Settings screen.
Raw logs remain local and are not committed. The log clock differs from the
visible Karoo clock; latency uses only timestamps from the same log.

Conclusion: matching camera-level success acknowledgements are available on this
setup. This supports implementing bounded acknowledgement-based retries, but
does not establish a guaranteed reset of the standby timer, a production timeout,
or behavior under response loss. No retry logic or longer interval was deployed.
