# Tablet Agent

A shared Python client, JSON CLI, and stdio MCP server for Mobilerun Portal's local WebSocket API. Works over Wi-Fi or an existing ADB port forward. No ADB dependency is needed for these tools.

## Install and configure

Requires Python 3.11+ and [uv](https://docs.astral.sh/uv/). From the repository root:

```bash
uv tool install --editable ./tools/tablet-agent
# If ~/.local/bin is not on PATH, use the executable's absolute path.
tablet profile add pad7 --url ws://192.168.178.10:8081/ --default
# Enter the Portal token at the prompt.
tablet status
```

Enable Portal's WebSocket Server, normally port **8081**. Port **8080** is its separate HTTP server. The tablet needs Portal's Accessibility Service for UI control and its storage access for file operations. Clipboard reading requires Mobilerun Keyboard to be selected.

Profiles live outside the checkout at `~/.config/tablet-agent/profiles.json` (or `$XDG_CONFIG_HOME/tablet-agent/profiles.json`). `TABLET_CONFIG` selects another profile file. `tablet profile list` omits tokens. To configure noninteractively, supply the token through `TABLET_TOKEN` or use `profile add --token-env VARIABLE_NAME`.

Selection order: `--profile NAME`, `TABLET_PROFILE`, saved default. `TABLET_URL` and `TABLET_TOKEN` override a saved connection or provide a connection without a profile. URLs contain only the endpoint; the client adds the token query parameter.

For USB, first create the forward externally, then save a localhost profile:

```bash
adb -s TABLET_SERIAL forward tcp:8081 tcp:8081
tablet profile add usb --url ws://127.0.0.1:8081/
tablet --profile usb status
```

## Agent workflow

1. Inspect the current screen.
2. Prefer a resource ID or exact visible text over remembered coordinates.
3. Supply an expected value/package when an action needs proof of its effect.
4. Inspect after an uncertain outcome before deciding whether to retry.

```bash
tablet inspect
tablet screenshot --output /tmp/tablet.png
tablet tap --id offset_value_display
tablet type "12" --id offset_value_display --clear
tablet clear --id offset_value_display
tablet key 4                       # Android Back
tablet swipe --direction up         # Finger moves upward
tablet global 2                    # Android Home

tablet app launch com.rhythmpad7.app
tablet wait --package com.rhythmpad7.app
tablet app stop com.android.settings
tablet app deep-link 'package:com.rhythmpad7.app' \
  --intent-action android.settings.APPLICATION_DETAILS_SETTINGS
tablet app install 'http://YOUR_HOST:8000/app-debug.apk'
```

The editable field examples change Portal's overlay offset; restore its previous value afterwards. Do not use touchscreen automation to simulate gameplay dance-mat presses.

Selectors accept a full resource ID (`com.mobilerun.portal:id/offset_value_display`) or its short name. `--text` matches exact text or a nonempty accessibility description; ambiguous targets fail instead of tapping an arbitrary match. Coordinates and bounds use **physical screen pixels**, not dp. Each action resolves the current tree and screen dimensions again to handle rotation.

```bash
tablet tap --x 500 --y 800 --expect-package com.android.settings
tablet tap --text 'Settings' --expect-text 'Connection Details'
tablet swipe --start-x 500 --start-y 1500 --end-x 500 --end-y 500 --duration 400
tablet wait --id offset_value_display --text '12' --wait-timeout 8
tablet inspect --full              # Original accessibility tree
```

Runtime output is JSON. Exit status is 0 for `ok:true`, 1 for runtime/verification failure; argument usage errors use argparse's normal stderr and exit status 2.

Interaction results distinguish:

- `accepted:true`: Portal returned success for the action.
- `verified:true`: the expected state was observed, the target received focus, or the installer/force-stop confirmation reported success.
- `verified:false`: the expected state wasn't reached, or stopping an app was only best effort. `ok` is false.
- `verified:null`: no effect was proven. Two equal snapshots can establish `settled:true`, but don't prove a tap or swipe worked. `screen_changed` is a separate observation.
- `error.outcome_unknown:true`: the request was sent but its reply was lost. Mutations are never automatically retried.

Typing with replacement and clearing automatically verify the selected field's text. App launches verify its foreground package. Deep links need a subsequent `wait` for the desired screen if semantic verification is required. Two equal snapshots aren't a guarantee that an app has finished all asynchronous work.

## Files, clipboard, watchdog, and raw API

Tablet paths are relative to `/sdcard`; host paths are local to the machine running the CLI/MCP server.

```bash
tablet files list Download
tablet files upload Download/test.txt --local /tmp/test.txt
tablet files download Download/test.txt --local /tmp/copied.txt
tablet files fetch Download/fetched.txt --url http://YOUR_HOST:8000/test.txt
tablet files push Download/test.txt --url http://YOUR_HOST:8000/upload
tablet files delete Download/test.txt

tablet clipboard get
tablet clipboard set 'hello'
tablet keep-awake status
tablet keep-awake on
tablet keep-awake off

tablet rpc overlay_offset --params '{"offset":0}'
tablet rpc socket_port --params '{"port":8080}'
tablet rpc packages
tablet rpc files/download --params '{"path":"Download/test.txt"}' --output /tmp/test.txt
```

`files push` sends HTTP POST; `fetch` retrieves a URL on the tablet. Download output includes a SHA-256 digest. File writes/deletes replace or remove the requested target. `rpc` exposes every Portal method without effect verification; binary replies require `--output`. `screenshot` accepts both UUID-prefixed binary PNG and base64 JSON responses. Upload/download support Portal's binary/base64 file formats.

`--timeout SECONDS` before the command controls ordinary RPCs. File transfers use 90 seconds, app launch/stop/deep-link use 45 seconds, and installation uses 180 seconds. Android may require installer dialogs or a Play Protect scan; use inspection and interaction from another client if needed. This tool does not serve APKs or automatically dismiss those dialogs.

## Diagnostics

```bash
tablet doctor
tablet doctor --full
```

The default checks are read-only: authenticated ping, version/state, package list, time, PNG screenshot, keep-awake status, and storage listing. `--full` also checks clipboard round-trip, keep-awake toggling, and upload/download with a uniquely named scratch file. It restores the clipboard/watchdog and deletes the file through a separate cleanup connection, including after a test failure. Cleanup failures are reported and make `ok:false`; a complete device/network outage can prevent restoration.

The report lists operations it doesn't exercise. Full diagnostics don't launch/stop apps, tap/type/swipe, install APKs, change the HTTP port/overlay offset, or test fetch/push. Those operations have explicit commands so a diagnostic doesn't disrupt the user's app or install software unexpectedly.

## MCP

After installation, `tablet-mcp` (or `tablet mcp`) runs the stdio server. It exposes:

| Tool | Purpose |
| --- | --- |
| `device_status` | Connection, version, foreground app |
| `inspect_screen` | Compact current elements or full tree |
| `take_screenshot` | Native MCP PNG image |
| `interact` | Tap, swipe, type, clear, key, global action |
| `wait_for_screen` | Wait for element/package or stable snapshots |
| `manage_app` | Launch, stop, deep link, install |
| `transfer_file` | List, upload, download, delete, fetch, push |
| `clipboard` | Get/set text |
| `keep_awake` | Query/toggle watchdog |
| `run_diagnostics` | Read-only or full diagnostics with cleanup |
| `rpc` | Raw method escape hatch |

Every tool accepts optional `profile`; otherwise the environment/default profile applies. Multi-step tools serialize within one server process. Separate CLI/server processes can still operate the tablet concurrently. Transport/config failures are MCP tool errors; action verification failures return structured `ok:false` results.

For Codex:

```bash
codex mcp add tablet-agent --env TABLET_PROFILE=pad7 -- /ABSOLUTE/PATH/TO/tablet-mcp
codex mcp get tablet-agent
```

Use `command -v tablet-mcp` to find that absolute path. For install/file operations, set `tool_timeout_sec = 240` in the `[mcp_servers.tablet-agent]` section of the local Codex configuration. See the [official MCP configuration documentation](https://learn.chatgpt.com/docs/extend/mcp?surface=cli). Reload the MCP connection or start a fresh client session after registration.

Other stdio MCP clients can use:

```json
{
  "mcpServers": {
    "tablet-agent": {
      "command": "/ABSOLUTE/PATH/TO/tablet-mcp",
      "env": {"TABLET_PROFILE": "pad7"}
    }
  }
}
```

## Development and validation

```bash
uv sync --project tools/tablet-agent --locked
uv run --project tools/tablet-agent python -m unittest discover -s tools/tablet-agent/tests -v
```

`client.py` owns transport/framing, `actions.py` owns selectors and verification, `diagnostics.py` owns reversible checks, and the CLI/MCP adapters call that shared code. Tests use a real local WebSocket server and an MCP stdio subprocess; no tablet is needed for the automated suite.

Live validation on Xiaomi Pad 7 with Portal **0.7.26**: all 12 full diagnostic checks and three cleanup steps passed; MCP status, native PNG screenshot, selector-based typing, text verification, and restoring the original value also passed.

Protocol reference: [Mobilerun local API](https://github.com/droidrun/mobilerun-portal/blob/main/docs/local-api.md).
