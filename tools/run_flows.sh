#!/usr/bin/env bash
# Run Maestro flows through the `phone` CLI (no Maestro driver app needed on the Xiaomi tablet).
# Force-stops the app over adb before each flow. Usage: tools/run_flows.sh [-s SERIAL] flow.yaml...
set -uo pipefail
SERIAL=4c637b9e
if [[ "${1:-}" == "-s" ]]; then SERIAL=$2; shift 2; fi
fail=0
for flow in "$@"; do
  adb -s "$SERIAL" shell am force-stop com.rhythmpad7.app
  out=$(phone -t 600 flow "$flow" 2>&1)
  if echo "$out" | python3 -c 'import sys,json; sys.exit(0 if json.load(sys.stdin).get("ok") else 1)' 2>/dev/null; then
    echo "PASS $flow"
  else
    fail=1
    echo "FAIL $flow"
    echo "$out" | python3 -c '
import sys,json
try:
  d=json.load(sys.stdin)
  bad=[r for r in d.get("results",[]) if not r["result"].get("ok")]
  for r in bad[-1:]: print("  step", r["step"], r["command"], r["result"].get("error"))
  if not bad: print("  ", d.get("error"))
except Exception as e: print("  unparsable output")'
  fi
done
exit $fail
