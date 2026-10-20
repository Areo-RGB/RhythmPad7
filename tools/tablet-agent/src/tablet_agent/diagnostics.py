"""Live checks with scratch files and restoration recorded in the report."""

import base64
import uuid

from .actions import Tablet
from .client import PortalClient
from .config import TabletError


async def doctor(client: PortalClient, *, full: bool = False) -> dict:
    checks = []
    cleanup = []
    restore = []

    async def check(name, operation, verify=lambda value: True):
        try:
            value = await operation()
            ok = bool(verify(value))
            checks.append({"check": name, "ok": ok})
            return value if ok else None
        except TabletError as exc:
            checks.append({"check": name, **exc.as_dict()})
            return None

    await check("connection", lambda: Tablet(client).status())
    await check(
        "packages", lambda: client.call("packages"), lambda x: isinstance(x, list)
    )
    await check(
        "time", lambda: client.call("time"), lambda x: isinstance(x, (int, float))
    )
    await check(
        "screenshot", client.screenshot, lambda x: x.startswith(b"\x89PNG\r\n\x1a\n")
    )
    awake = await check(
        "keep_awake_status",
        lambda: client.call("screen/keepAwake/status"),
        lambda x: isinstance(x, dict),
    )
    await check("files_list", lambda: client.call("files/list", {"path": "Download"}))
    if full:
        try:
            try:
                original = await client.call("clipboard/get")
                if not isinstance(original, str):
                    raise TabletError(
                        "Clipboard format cannot be restored", "protocol_error"
                    )
                restore.append(
                    ("clipboard_restore", "clipboard/set", {"text": original})
                )
                text = "tablet-agent diagnostic " + uuid.uuid4().hex
                await check(
                    "clipboard_set",
                    lambda: client.call("clipboard/set", {"text": text}),
                )
                await check(
                    "clipboard_roundtrip",
                    lambda: client.call("clipboard/get"),
                    lambda x: x == text,
                )
            except TabletError as exc:
                checks.append({"check": "clipboard_roundtrip", **exc.as_dict()})
            if awake is not None and isinstance(awake.get("enabled"), bool):
                original_enabled = awake["enabled"]
                restore.append(
                    (
                        "keep_awake_restore",
                        "screen/keepAwake/set",
                        {"enabled": original_enabled},
                    )
                )
                await check(
                    "keep_awake_toggle",
                    lambda: client.call(
                        "screen/keepAwake/set", {"enabled": not original_enabled}
                    ),
                    lambda x: (
                        isinstance(x, dict) and x.get("enabled") is (not original_enabled)
                    ),
                )
                await check(
                    "keep_awake_readback",
                    lambda: client.call("screen/keepAwake/status"),
                    lambda x: (
                        isinstance(x, dict) and x.get("enabled") is (not original_enabled)
                    ),
                )
            path = "Download/tablet-agent-test-" + uuid.uuid4().hex + ".txt"
            restore.append(("scratch_file_delete", "files/delete", {"path": path}))
            payload = b"tablet-agent file roundtrip\n"
            await check(
                "files_upload",
                lambda: client.call(
                    "files/upload",
                    {"path": path, "data": base64.b64encode(payload).decode()},
                ),
            )

            def matches(value):
                if isinstance(value, str):
                    try:
                        value = base64.b64decode(value, validate=True)
                    except ValueError:
                        return False
                return value == payload

            await check(
                "files_download_roundtrip",
                lambda: client.call("files/download", {"path": path}),
                matches,
            )
        finally:
            # A separate connection also permits cleanup after a request timeout closed the first one.
            try:
                async with PortalClient(
                    client.device, timeout=client.timeout
                ) as clean_client:
                    for name, method, params in reversed(restore):
                        try:
                            await clean_client.call(method, params)
                            cleanup.append({"check": name, "ok": True})
                        except TabletError as exc:
                            cleanup.append({"check": name, **exc.as_dict()})
            except TabletError as exc:
                cleanup.append({"check": "cleanup_connection", **exc.as_dict()})
    return {
        "ok": all(c["ok"] for c in checks + cleanup),
        "mode": "full" if full else "read_only",
        "checks": checks,
        "cleanup": cleanup,
        "not_tested": [
            "gestures",
            "keyboard",
            "app_launch_stop_deep_link",
            "install",
            "socket_port",
            "overlay_offset",
            "files_fetch_push",
        ],
    }
