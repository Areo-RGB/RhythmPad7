"""JSON CLI for the upstream Mobile Harness integration."""

from __future__ import annotations

import argparse
import json
import os
import sys

from .client import HarnessConfig, RhythmPadHarness


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="rhythmpad-harness")
    parser.add_argument("--url", default=os.environ.get("MOBILERUN_ANDROID_PORTAL_URL", "http://127.0.0.1:18080"))
    parser.add_argument("--token", default=os.environ.get("MOBILERUN_ANDROID_PORTAL_TOKEN"))
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("status")
    commands.add_parser("inspect")
    screenshot = commands.add_parser("screenshot")
    screenshot.add_argument("--output", required=True)
    tap = commands.add_parser("tap")
    tap.add_argument("--text")
    tap.add_argument("--resource-id")
    tap.add_argument("--x", type=int)
    tap.add_argument("--y", type=int)
    type_command = commands.add_parser("type")
    type_command.add_argument("value")
    type_command.add_argument("--text")
    type_command.add_argument("--resource-id")
    type_command.add_argument("--clear", action=argparse.BooleanOptionalAction, default=True)
    launch = commands.add_parser("launch")
    launch.add_argument("package")
    launch.add_argument("--activity")
    commands.add_parser("diagnostics")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    if not args.token:
        print(json.dumps({"ok": False, "error": "Set MOBILERUN_ANDROID_PORTAL_TOKEN or --token"}))
        raise SystemExit(1)
    try:
        harness = RhythmPadHarness(HarnessConfig(args.url.rstrip("/"), args.token))
        if args.command == "status":
            result = harness.status()
        elif args.command == "inspect":
            result = harness.inspect()
        elif args.command == "screenshot":
            result = harness.screenshot(args.output)
        elif args.command == "tap":
            result = harness.tap(text=args.text, resource_id=args.resource_id, x=args.x, y=args.y)
        elif args.command == "type":
            result = harness.type(args.value, text=args.text, resource_id=args.resource_id, clear=args.clear)
        elif args.command == "launch":
            result = harness.launch(args.package, args.activity)
        else:
            result = harness.diagnostics()
    except Exception as exc:
        result = {"ok": False, "error": {"type": type(exc).__name__, "message": str(exc)}}
    print(json.dumps(result, ensure_ascii=False))
    raise SystemExit(0 if result.get("ok") else 1)
