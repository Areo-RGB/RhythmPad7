"""Thin MCP stdio adapter. All device logic is shared with the CLI."""

import asyncio
import json
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Literal

from mcp.server.fastmcp import FastMCP, Image
from mcp.server.fastmcp.exceptions import ToolError
from mcp.types import ToolAnnotations

from .actions import Tablet
from .client import PortalClient
from .config import TabletError, resolve_device
from .diagnostics import doctor

mcp = FastMCP(
    "Tablet Agent",
    instructions=(
        "Use inspect_screen to get current resource IDs and bounds before interacting. "
        "Coordinates are physical screen pixels, not dp. Prefer resource_id selectors. "
        "accepted means the Portal accepted a command; verified=null means no effect was proven. "
        "Use expect_text/expect_package for verification. Never blindly retry outcome_unknown actions. "
        "run_diagnostics is read-only unless full=true; full restores its temporary changes. "
        "Tools are serialized within this server."
    ),
)
_device_lock = asyncio.Lock()
READ_ONLY = ToolAnnotations(
    readOnlyHint=True, destructiveHint=False, idempotentHint=True
)


@asynccontextmanager
async def device(profile: str | None):
    try:
        async with _device_lock, PortalClient(resolve_device(profile)) as client:
            yield client
    except TabletError as exc:
        raise ToolError(json.dumps(exc.as_dict())) from None
    except (OSError, ValueError) as exc:
        raise ToolError(
            json.dumps(
                {"ok": False, "error": {"code": "local_error", "message": str(exc)}}
            )
        ) from None


@mcp.tool(annotations=READ_ONLY)
async def device_status(profile: str | None = None) -> dict:
    """Check authenticated WebSocket ping, Portal version, and current foreground app."""
    async with device(profile) as client:
        return await Tablet(client).status()


@mcp.tool(annotations=READ_ONLY)
async def inspect_screen(profile: str | None = None, full: bool = False) -> dict:
    """Read fresh screen elements, resource IDs, physical pixel bounds, focus, and app. full returns the original tree."""
    async with device(profile) as client:
        return await Tablet(client).inspect(full=full)


@mcp.tool(annotations=READ_ONLY)
async def take_screenshot(
    profile: str | None = None, hide_overlay: bool = True
) -> Image:
    """Return the current tablet screen as a native PNG image block."""
    async with device(profile) as client:
        return Image(data=await client.screenshot(hide_overlay), format="png")


@mcp.tool()
async def interact(
    action: Literal["tap", "swipe", "type", "clear", "key", "global"],
    profile: str | None = None,
    resource_id: str | None = None,
    text: str | None = None,
    x: int | None = None,
    y: int | None = None,
    value: str | None = None,
    clear: bool = True,
    key_code: int | None = None,
    direction: Literal["up", "down", "left", "right"] = "up",
    start_x: int | None = None,
    start_y: int | None = None,
    end_x: int | None = None,
    end_y: int | None = None,
    duration: int = 400,
    global_action: int | None = None,
    expect_text: str | None = None,
    expect_package: str | None = None,
    wait_timeout: float = 4,
) -> dict:
    """Perform an action, then inspect. Match exact text or resource_id; ambiguous taps fail. type/clear verify field contents. Other actions need expectations to prove effects. Swipe direction follows finger movement. Android global IDs: Back=1, Home=2, Recents=3."""
    async with device(profile) as client:
        return await Tablet(client).interact(
            action,
            resource_id=resource_id,
            text=text,
            x=x,
            y=y,
            value=value,
            clear=clear,
            key_code=key_code,
            direction=direction,
            start_x=start_x,
            start_y=start_y,
            end_x=end_x,
            end_y=end_y,
            duration=duration,
            global_action=global_action,
            expect_text=expect_text,
            expect_package=expect_package,
            wait_timeout=wait_timeout,
        )


@mcp.tool(annotations=READ_ONLY)
async def wait_for_screen(
    profile: str | None = None,
    resource_id: str | None = None,
    text: str | None = None,
    package: str | None = None,
    timeout: float = 6,
) -> dict:
    """Poll for a visible element or foreground package. With no selector, wait for two equal snapshots."""
    from .actions import compact

    async with device(profile) as client:
        state, verification = await Tablet(client).wait(
            resource_id=resource_id, text=text, package=package, timeout=timeout
        )
        return {
            "ok": verification["settled"] and verification["verified"] is not False,
            **verification,
            "state": compact(state),
        }


@mcp.tool()
async def manage_app(
    action: Literal["launch", "stop", "deep-link", "install"],
    profile: str | None = None,
    package: str | None = None,
    activity: str | None = None,
    stop_before_launch: bool = False,
    deep_link: str | None = None,
    intent_action: str = "android.intent.action.VIEW",
    display_id: int = 0,
    urls: list[str] | None = None,
    wait_timeout: float = 6,
) -> dict:
    """Launch, deep-link, stop, or install APK URLs. Launch verifies foreground package; stop requires UI force-stop confirmation to report verified=true. Install may require Android dialogs."""
    async with device(profile) as client:
        return await Tablet(client).app(
            action,
            package=package,
            activity=activity,
            stop_before_launch=stop_before_launch,
            deep_link=deep_link,
            intent_action=intent_action,
            display_id=display_id,
            urls=urls,
            wait_timeout=wait_timeout,
        )


@mcp.tool()
async def transfer_file(
    action: Literal["list", "upload", "download", "delete", "fetch", "push"],
    path: str,
    profile: str | None = None,
    local: str | None = None,
    url: str | None = None,
) -> dict:
    """Access tablet storage. path is relative to /sdcard (e.g. Download/test.txt). upload/download need a host local path; fetch/push need URL. push sends HTTP POST."""
    async with device(profile) as client:
        return await Tablet(client).transfer(action, path=path, local=local, url=url)


@mcp.tool()
async def clipboard(
    action: Literal["get", "set"], profile: str | None = None, text: str | None = None
) -> dict:
    """Read/write clipboard; reading requires Mobilerun Keyboard. Setting replaces clipboard contents."""
    if action == "set" and text is None:
        raise ToolError("clipboard set requires text")
    async with device(profile) as client:
        return {
            "ok": True,
            "result": await client.call(
                "clipboard/" + action, {"text": text} if action == "set" else {}
            ),
        }


@mcp.tool()
async def keep_awake(
    setting: Literal["status", "on", "off"] = "status", profile: str | None = None
) -> dict:
    """Query or change the tablet screen-awake watchdog."""
    async with device(profile) as client:
        return {
            "ok": True,
            "result": await client.call(
                "screen/keepAwake/" + ("status" if setting == "status" else "set"),
                {} if setting == "status" else {"enabled": setting == "on"},
            ),
        }


@mcp.tool()
async def run_diagnostics(profile: str | None = None, full: bool = False) -> dict:
    """Check connectivity, state, screenshot, packages, time, file access, and watchdog. full also round-trips clipboard and a scratch file, toggles watchdog, then reports cleanup. Doesn't launch apps or install APKs."""
    async with device(profile) as client:
        return await doctor(client, full=full)


@mcp.tool()
async def rpc(
    method: str,
    profile: str | None = None,
    params: dict | None = None,
    output: str | None = None,
) -> dict:
    """Raw Portal method escape hatch. No effect verification. Binary responses require a host output path."""
    async with device(profile) as client:
        result = await client.call(method, params)
        if isinstance(result, bytes):
            if not output:
                raise ToolError("Binary response requires output path")
            path = Path(output).expanduser().resolve()
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(result)
            return {"ok": True, "path": str(path), "bytes": len(result)}
        return {"ok": True, "result": result}


def main():
    mcp.run(transport="stdio")
