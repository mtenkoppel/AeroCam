#!/usr/bin/env python3
"""Decode private FIT data locally. No network or production automation logic."""
import argparse
import datetime as dt
import json
from pathlib import Path
import fitdecode

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('fit', type=Path)
parser.add_argument('output', type=Path)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
records, events = [], []
with fitdecode.FitReader(str(args.fit)) as reader:
    for frame in reader:
        if not isinstance(frame, fitdecode.FitDataMessage):
            continue
        if frame.name not in ('record', 'event', 'session', 'lap'):
            continue
        row = {f.name: f.value for f in frame.fields}
        if row.get('timestamp') is None:
            continue
        if frame.name == 'record':
            records.append(row)
        else:
            events.append(dict(type=frame.name, **row))
if not records:
    raise SystemExit('No timestamped FIT records')
records.sort(key=lambda r: r['timestamp'])
start = records[0]['timestamp']
def sec(timestamp):
    n = (timestamp-start).total_seconds()
    if n != int(n):
        raise ValueError('This harness currently requires whole-second FIT timestamps')
    return int(n)
by_second = {sec(r['timestamp']): r for r in records}
if len(by_second) != len(records):
    raise SystemExit('Duplicate record timestamps: resolve them explicitly before replay')
timer = {}
for event in events:
    if event.get('event') == 'timer':
        typ = event.get('event_type', '')
        if typ == 'start':
            timer[sec(event['timestamp'])] = 'start'
        elif typ.startswith('stop'):
            timer[sec(event['timestamp'])] = 'stop' if event.get('timer_trigger') == 'auto' else 'manual_stop'
end = max(max(by_second), max((sec(e['timestamp']) for e in events if e['type'] == 'session'), default=0))
if end > max(by_second):
    timer[end] = 'end'
rows = []
for t in range(end+1):
    r = by_second.get(t, {})
    speed = r.get('enhanced_speed', r.get('speed'))
    values = [t, speed*3.6 if speed is not None else None, r.get('heart_rate'), r.get('grade'), timer.get(t, '-')]
    rows.append('\t'.join('' if v is None else str(v) for v in values))
(args.output/'replay.tsv').write_text('\n'.join(rows)+'\n')
(args.output/'fit-decoded.json').write_text(json.dumps({'records': records, 'events': events}, default=str))
print(f'{len(records)} records; {end} elapsed seconds; {len(timer)} timer/session events')
print('No grade is inferred from altitude. Missing samples exercise production freshness handling.')
