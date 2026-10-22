#!/usr/bin/env bash
# Copy StepMania song folders (each with .ssc/.sm + audio) to /sdcard/RhythmPad7/Songs on the device.
# Usage: tools/push_songs.sh [-s SERIAL] "<song dir>" ["<song dir>" ...]   then restart the app.
set -euo pipefail
ADB=(adb)
if [[ "${1:-}" == "-s" ]]; then ADB+=(-s "$2"); shift 2; fi
"${ADB[@]}" shell mkdir -p /sdcard/RhythmPad7/Songs
for dir in "$@"; do "${ADB[@]}" push "$dir" /sdcard/RhythmPad7/Songs/ | tail -1; done
# Grant "All files access" so the app can read the shared folder (same as the settings toggle)
"${ADB[@]}" shell appops set com.rhythmpad7.app MANAGE_EXTERNAL_STORAGE allow
