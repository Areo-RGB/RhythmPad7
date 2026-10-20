# RhythmPad7 + Mobile Harness

This is a separate integration workspace for RhythmPad7 and Droidrun Mobile Harness.

`vendor/mobile-harness` is pinned to the upstream [mobile-harness](https://github.com/droidrun/mobile-harness) repository. That repository is the agent skill and documentation; the runtime control API is `mobilerun_core.Mobilerun`. `tools/mobile-harness` is the small RhythmPad7 adapter that uses the upstream API against the Xiaomi Pad 7's Portal HTTP endpoint.

The original `/home/paul/Projects/RhythmPad7` checkout and its WebSocket CLI/MCP stay independent. This sibling checkout is intended for comparing the upstream harness path with our richer WebSocket path.

## Setup

Use Python 3.11–3.13, as required by the upstream harness:

```bash
cd /home/paul/Projects/RhythmPad7-mobile-harness
uv sync --project tools/mobile-harness
uv run --project tools/mobile-harness rhythmpad-harness status
```

Configure the Portal HTTP endpoint and token outside the repository:

```bash
export MOBILERUN_ANDROID_PORTAL_URL=http://192.168.178.10:8080
export MOBILERUN_ANDROID_PORTAL_TOKEN='your-token'
```

The HTTP endpoint is used here because Mobile Harness's documented local Android backends are ADB-first or Portal HTTP-only. The upstream API does not use the Portal WebSocket endpoint directly.

## Commands

```bash
rhythmpad-harness status
rhythmpad-harness inspect
rhythmpad-harness screenshot --output /tmp/rhythmpad-harness.png
rhythmpad-harness tap --resource-id com.mobilerun.portal:id/offset_value_display
rhythmpad-harness type --resource-id com.mobilerun.portal:id/offset_value_display 12
rhythmpad-harness launch com.mobilerun.portal
rhythmpad-harness diagnostics
```

The adapter uses accessibility resource IDs/text for interactions. It reports foreground package after launches. A coordinate tap is available for cases where the accessibility tree has no suitable element. It does not simulate RhythmPad7 gameplay input; the physical Microntek dance mat remains the gameplay input source.

## What is combined

| Layer | Role |
| --- | --- |
| `vendor/mobile-harness` | Pinned upstream agent instructions and Android operating rules |
| `mobilerun-core[local]` | Official `Mobilerun` runtime for local Portal/ADB/cloud backends |
| `tools/mobile-harness` | RhythmPad7-specific status, inspection, screenshot, app, and text helpers |
| `tools/tablet-agent` | Existing WebSocket CLI/MCP for Portal methods not exposed by the upstream facade, including files, keep-awake, raw RPC, and effect verification |

Use Mobile Harness for the standard observe–act–verify flow. Use the WebSocket adapter for Portal-specific methods and binary/event behavior.

## Validation

```bash
uv run --project tools/mobile-harness python -m unittest discover -s tools/mobile-harness/tests -v
```

The live tablet test should run from this workspace after setting the two Portal environment variables:

```bash
rhythmpad-harness status
rhythmpad-harness diagnostics
```
