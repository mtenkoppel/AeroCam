# Keep awake recovery and developer ride diagnostics

## Behavior

- A new connection waits the configured interval before the first QS. Default:
  270 seconds, requiring five-minute camera Auto Power Off. Saved custom values
  are preserved. Guided standby selection is not implemented by this change.
- Enabling Keep awake while connected sends immediately, preserving the explicit
  button action. Stopping recording also sends immediately, preserving the tested
  behavior rather than assuming that recording resets the standby timer.
- An attempt waits five seconds for a matching 0x0011 success response. At most
  three transmissions occur per recovery cycle. Each uses a fresh sequence.
- A late matching success ends recovery; next due time is that attempt's actual
  send time plus the interval. Unrelated or previous-connection replies cannot
  complete the current cycle.
- Recording, disabling Keep awake, or disconnecting cancels pending QS work.
  An in-flight write cannot be recalled. Retries must pass the recording guard.
- Malformed/unknown/nonzero replies do not count as success. The timeout bounds
  retries. Explicit parsing error 0x01 ends recovery immediately.
- After exhaustion or a five-second stalled queue/write, Keep awake is marked
  inactive and a status message explains failure. No unbounded retry loop or
  automatic Bluetooth reset is added. Toggle Keep awake or reconnect to retry.
- State updates for connection/notifications are serialized on the existing main
  handler; old GATT instances are ignored for these callbacks and write results.
  This is limited integration needed to cancel/match the recovery cycle, not a
  redesign of reconnection. Other connection recovery remains unchanged.

## Developer-only logging

After opening this app version once, connect USB and run:

```sh
./scripts/ride-diagnostics.sh enable
# Ride; USB can be disconnected and the Karoo restarted.
./scripts/ride-diagnostics.sh collect /tmp/aerocam-ride-review
./scripts/ride-diagnostics.sh disable
```

Logging is off unless the developer explicitly creates the opt-in marker via the
script. There is no rider setting and nothing is uploaded. Disabling retains the
existing logs. They live in the app-specific external-files diagnostics folder,
are accessible over authorized ADB, and survive process restarts/reboots and
in-place updates, but not uninstall/data removal. This is developer convenience,
not encrypted secret storage.

Two rotating files (`ride.previous.log`, then `ride.log`) total at most 512 KiB.
Each line is truncated to 400 characters. A single background writer has at most
128 pending events and reports dropped events after overload/storage failures.
No unbounded in-memory capture, automatic logcat dump, raw BLE packets, GPS,
camera addresses, or pairing credentials are included. If retention fills, oldest
entries are intentionally replaced; no fixed number of rides is guaranteed.

Each event includes UTC wall-clock and elapsed milliseconds. Read these events:

- `LOG_START`: app/Android/build context; `LOG_DROPPED`: incomplete evidence.
- `LINK_READY`, `LINK_DISCONNECTED`, `LINK_STALE`, `LINK_CLOSED`: connection
  transitions and reasons (a Bluetooth disconnect is not necessarily power-off).
- `KEEP_AWAKE_SETTING`, `RECORDING`, `QS_SCHEDULED`: whether QS was permitted/due.
- `QS_SENT`, `QS_WRITE_RESULT`, `QS_RESPONSE`, `QS_ACK`: actual send sequence,
  Android write result, DJI result and acknowledgement latency.
- `QS_RETRY`, `QS_UNCONFIRMED`: retry or terminal failure, with reason.

For a reported drop, find the time and preceding connection/settings/QS events.
Differentiate stale RX, explicit GATT disconnect, failed DJI acknowledgements,
and a stalled Android write. Logs support diagnosis; do not claim a cause that
was not observed, or absence of an event when `LOG_DROPPED`/rotation occurred.

## Compatibility and validation

Checked official Karoo SDK documentation and releases on 2026-11-02: SDK remains
1.1.9, documented KOS minimum 1.634.2440. No dependency/SDK upgrade or permission
change. Sources: https://hammerheadnav.github.io/karoo-ext/ and
https://github.com/hammerheadnav/karoo-ext/releases .

Baseline hardware acknowledgement evidence is recorded in
QS_ACKNOWLEDGEMENT_EXPERIMENT.md (version 20, Action 4, Android 12 / Karoo k24).
Do not attribute that baseline test to this new scheduler. New scheduler hardware
checks are pending: first connection delay; recording start/stop cancellation;
Keep awake off during recovery; camera-on boot, delayed power-on, out-of-range,
reboot and deliberate power-off. No more camera behavior experiments are required
before review, but those release regression checks must not be claimed as passed.

Automated checks cover response matching, malformed replies, late success send
anchors, bounded attempts, and rotating-log disk limits. Real missing callbacks
and lost-response injection are not covered by those unit tests.

Rollback: keep the pre-change commit 2bf146f available. For device rollback,
rebuild it with a higher versionCode and the established release key; never
uninstall or clear pairing to downgrade. This branch is a test build, not a release.
