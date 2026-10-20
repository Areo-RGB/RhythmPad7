"""JSON CLI; binary responses are written to files, never terminal stdout."""

import argparse
import asyncio
import getpass
import json
import os
import sys
from pathlib import Path

from .actions import Tablet
from .client import PortalClient
from .config import TabletError, list_profiles, resolve_device, save_profile
from .diagnostics import doctor


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(
        prog="tablet",
        description="Control Mobilerun Portal over its local WebSocket API",
    )
    root.add_argument("--profile", help="Saved profile (or TABLET_PROFILE)")
    root.add_argument(
        "--timeout", type=float, default=20, help="RPC timeout in seconds"
    )
    sub = root.add_subparsers(dest="command", required=True)
    profile = sub.add_parser("profile").add_subparsers(
        dest="profile_command", required=True
    )
    add = profile.add_parser("add")
    add.add_argument("name")
    add.add_argument("--url", required=True, help="ws://tablet-ip:8081/")
    add.add_argument(
        "--token-env",
        default="TABLET_TOKEN",
        help="Environment variable containing the token; otherwise prompt",
    )
    add.add_argument("--default", action="store_true")
    profile.add_parser("list")
    sub.add_parser("status")
    inspect = sub.add_parser("inspect")
    inspect.add_argument("--full", action="store_true")
    screenshot = sub.add_parser("screenshot")
    screenshot.add_argument("--output", required=True)
    screenshot.add_argument("--show-overlay", action="store_true")

    def selector(p):
        p.add_argument("--id", dest="resource_id")
        p.add_argument("--text", help="Exact visible text or description")

    def expectation(p):
        p.add_argument("--expect-text")
        p.add_argument("--expect-package")
        p.add_argument("--wait-timeout", type=float, default=4)

    tap = sub.add_parser("tap")
    selector(tap)
    expectation(tap)
    tap.add_argument("--x", type=int)
    tap.add_argument("--y", type=int)
    type_cmd = sub.add_parser("type")
    selector(type_cmd)
    expectation(type_cmd)
    type_cmd.add_argument("value")
    type_cmd.add_argument(
        "--clear", action=argparse.BooleanOptionalAction, default=True
    )
    clear = sub.add_parser("clear")
    selector(clear)
    expectation(clear)
    swipe = sub.add_parser("swipe")
    expectation(swipe)
    swipe.add_argument(
        "--direction", choices=["up", "down", "left", "right"], default="up"
    )
    for name in ("start-x", "start-y", "end-x", "end-y", "duration"):
        swipe.add_argument(
            "--" + name, type=int, default=400 if name == "duration" else None
        )
    key = sub.add_parser("key")
    key.add_argument("key_code", type=int)
    expectation(key)
    glob = sub.add_parser("global")
    glob.add_argument("global_action", type=int)
    expectation(glob)
    wait = sub.add_parser("wait")
    selector(wait)
    wait.add_argument("--package")
    wait.add_argument("--wait-timeout", type=float, default=6)
    app = sub.add_parser("app").add_subparsers(dest="app_action", required=True)
    launch = app.add_parser("launch")
    launch.add_argument("package")
    launch.add_argument("--activity")
    launch.add_argument("--stop-before-launch", action="store_true")
    stop = app.add_parser("stop")
    stop.add_argument("package")
    deep = app.add_parser("deep-link")
    deep.add_argument("deep_link")
    deep.add_argument("--package")
    deep.add_argument("--intent-action", default="android.intent.action.VIEW")
    deep.add_argument("--display-id", type=int, default=0)
    install = app.add_parser("install")
    install.add_argument("urls", nargs="+")
    files = sub.add_parser("files").add_subparsers(dest="file_action", required=True)
    for action in ("list", "upload", "download", "delete", "fetch", "push"):
        p = files.add_parser(action)
        p.add_argument("path")
        if action in ("upload", "download"):
            p.add_argument("--local", required=True)
        if action in ("fetch", "push"):
            p.add_argument("--url", required=True)
    clipboard = sub.add_parser("clipboard").add_subparsers(
        dest="clipboard_action", required=True
    )
    clipboard.add_parser("get")
    clip_set = clipboard.add_parser("set")
    clip_set.add_argument("text")
    keep = sub.add_parser("keep-awake")
    keep.add_argument("setting", choices=["status", "on", "off"])
    rpc = sub.add_parser("rpc")
    rpc.add_argument("method")
    rpc.add_argument("--params", default="{}")
    rpc.add_argument("--output", help="Required when the response is binary")
    diag = sub.add_parser("doctor")
    diag.add_argument(
        "--full",
        action="store_true",
        help="Also round-trip clipboard/files and restore keep-awake",
    )
    sub.add_parser("mcp", help="Run the MCP stdio server")
    return root


async def execute(args) -> dict:
    if args.command == "profile":
        if args.profile_command == "list":
            return list_profiles()
        token = os.environ.get(args.token_env) or getpass.getpass("Portal token: ")
        return save_profile(args.name, args.url, token, default=args.default)
    async with PortalClient(resolve_device(args.profile), args.timeout) as client:
        tablet = Tablet(client)
        if args.command == "status":
            return await tablet.status()
        if args.command == "inspect":
            return await tablet.inspect(full=args.full)
        if args.command == "screenshot":
            data = await client.screenshot(not args.show_overlay)
            path = Path(args.output).expanduser().resolve()
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
            return {
                "ok": True,
                "path": str(path),
                "bytes": len(data),
                "mime_type": "image/png",
            }
        if args.command in ("tap", "type", "clear", "swipe", "key", "global"):
            values = vars(args).copy()
            for name in ("command", "profile", "timeout"):
                values.pop(name)
            return await tablet.interact(args.command, **values)
        if args.command == "wait":
            state, verification = await tablet.wait(
                resource_id=args.resource_id,
                text=args.text,
                package=args.package,
                timeout=args.wait_timeout,
            )
            from .actions import compact

            return {
                "ok": verification["settled"] and verification["verified"] is not False,
                **verification,
                "state": compact(state),
            }
        if args.command == "app":
            values = vars(args).copy()
            for name in ("command", "profile", "timeout", "app_action"):
                values.pop(name)
            return await tablet.app(args.app_action, **values)
        if args.command == "files":
            return await tablet.transfer(
                args.file_action,
                path=args.path,
                local=getattr(args, "local", None),
                url=getattr(args, "url", None),
            )
        if args.command == "clipboard":
            return {
                "ok": True,
                "result": await client.call(
                    "clipboard/" + args.clipboard_action,
                    {"text": args.text} if args.clipboard_action == "set" else {},
                ),
            }
        if args.command == "keep-awake":
            return {
                "ok": True,
                "result": await client.call(
                    "screen/keepAwake/"
                    + ("status" if args.setting == "status" else "set"),
                    {}
                    if args.setting == "status"
                    else {"enabled": args.setting == "on"},
                ),
            }
        if args.command == "doctor":
            return await doctor(client, full=args.full)
        if args.command == "rpc":
            try:
                params = json.loads(args.params)
            except ValueError:
                raise TabletError(
                    "--params must be a JSON object", "invalid_argument"
                ) from None
            if not isinstance(params, dict):
                raise TabletError("--params must be a JSON object", "invalid_argument")
            result = await client.call(args.method, params)
            if isinstance(result, bytes):
                if not args.output:
                    raise TabletError(
                        "Binary response requires --output", "output_required"
                    )
                path = Path(args.output).expanduser().resolve()
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(result)
                return {"ok": True, "path": str(path), "bytes": len(result)}
            return {"ok": True, "result": result}
    raise TabletError("Unknown command", "invalid_argument")


def main():
    args = parser().parse_args()
    if args.command == "mcp":
        if args.profile:
            os.environ["TABLET_PROFILE"] = args.profile
        from .server import main as serve

        serve()
        return
    try:
        result = asyncio.run(execute(args))
    except TabletError as exc:
        result = exc.as_dict()
    except (OSError, ValueError) as exc:
        result = {"ok": False, "error": {"code": "local_error", "message": str(exc)}}
    except KeyboardInterrupt:
        result = {
            "ok": False,
            "error": {
                "code": "interrupted",
                "message": "Interrupted; an in-flight action may have completed",
            },
        }
    print(json.dumps(result, ensure_ascii=False))
    sys.exit(0 if result.get("ok") else 1)
