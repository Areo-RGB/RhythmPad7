"""Screen selectors, bounded waits, and shared high-level operations."""

import asyncio
import base64
import hashlib
import json
import re
import time
from pathlib import Path

from .client import PortalClient
from .config import TabletError


def walk_tree(tree):
    if isinstance(tree, list):
        for node in tree:
            yield from walk_tree(node)
    elif isinstance(tree, dict):
        yield tree
        yield from walk_tree(tree.get("children", []))


def bounds(node: dict) -> dict | None:
    b = node.get("boundsInScreen") or node.get("bounds")
    if isinstance(b, str):
        values = re.findall(r"-?\d+", b)
        b = (
            dict(zip(("left", "top", "right", "bottom"), map(int, values)))
            if len(values) == 4
            else None
        )
    if not isinstance(b, dict) or not all(
        isinstance(b.get(k), (int, float)) for k in ("left", "top", "right", "bottom")
    ):
        return None
    return b


def elements(state: dict) -> list[dict]:
    result = []
    for node in walk_tree(state.get("a11y_tree", [])):
        if node.get("isVisibleToUser") is False:
            continue
        b = bounds(node)
        text = node.get("text", "")
        rid = node.get("resourceId", "").replace("\\/", "/")
        desc = node.get("contentDescription", "")
        if not (
            rid or text or desc or node.get("isClickable") or node.get("isEditable")
        ):
            continue
        result.append(
            {
                "resource_id": rid,
                "text": text,
                "description": desc,
                "bounds": b,
                "clickable": node.get("isClickable", False),
                "editable": node.get("isEditable", False),
                "focused": node.get("isFocused", False),
                "enabled": node.get("isEnabled", True),
                "checked": node.get("isChecked"),
                "selected": node.get("isSelected", False),
            }
        )
    return result


def screen_size(state: dict) -> tuple[int, int]:
    boxes = [bounds(n) for n in walk_tree(state.get("a11y_tree", []))]
    boxes = [b for b in boxes if b]
    if not boxes:
        raise TabletError("No screen bounds available", "verification_failed")
    return int(max(b["right"] for b in boxes)), int(max(b["bottom"] for b in boxes))


def compact(state: dict, limit: int = 300) -> dict:
    found = elements(state)
    try:
        width, height = screen_size(state)
    except TabletError:
        width, height = None, None
    return {
        "phone_state": state.get("phone_state", {}),
        "width": width,
        "height": height,
        "elements": found[:limit],
        "total_elements": len(found),
        "truncated": len(found) > limit,
    }


def select(
    state: dict, resource_id: str | None = None, text: str | None = None
) -> dict:
    if not resource_id and text is None:
        raise TabletError("Supply resource_id or exact text", "invalid_argument")
    found = [
        e
        for e in elements(state)
        if (
            not resource_id
            or e["resource_id"] == resource_id
            or e["resource_id"].endswith("/" + resource_id)
        )
        and (
            text is None
            or e["text"] == text
            or (bool(text) and e["description"] == text)
        )
    ]
    # Duplicate roots occur on some Portal versions; don't make those ambiguous.
    found = list({json.dumps(e, sort_keys=True): e for e in found}.values())
    if not found:
        raise TabletError(
            "No visible element matches the selector", "element_not_found"
        )
    if len(found) != 1:
        raise TabletError(
            f"Selector matches {len(found)} elements; use a full resource_id or add text",
            "ambiguous_selector",
        )
    node = found[0]
    b = node["bounds"]
    if (
        not node["enabled"]
        or not b
        or b["right"] <= b["left"]
        or b["bottom"] <= b["top"]
    ):
        raise TabletError(
            "Selected element is disabled or has no usable bounds", "invalid_target"
        )
    return node


def fingerprint(state: dict) -> str:
    value = {"phone_state": state.get("phone_state", {}), "elements": elements(state)}
    return hashlib.sha256(json.dumps(value, sort_keys=True).encode()).hexdigest()


class Tablet:
    def __init__(self, client: PortalClient):
        self.client = client

    async def inspect(self, *, full: bool = False) -> dict:
        state = await self.client.state()
        return {"ok": True, "state": state if full else compact(state)}

    async def status(self) -> dict:
        latency = await self.client.ping()
        version = await self.client.call("version")
        state = await self.client.state()
        return {
            "ok": True,
            "profile": self.client.device.name,
            "url": self.client.device.url,
            "version": version,
            "ping_ms": round(latency * 1000, 2),
            "phone_state": state.get("phone_state", {}),
        }

    async def wait(
        self,
        *,
        resource_id: str | None = None,
        text: str | None = None,
        package: str | None = None,
        timeout: float = 4,
    ) -> tuple[dict, dict]:
        if timeout <= 0:
            raise TabletError("Wait timeout must be positive", "invalid_argument")
        deadline = time.monotonic() + timeout
        previous = None
        latest = None
        # Always yield to the device before reading its post-action state.
        await asyncio.sleep(min(0.25, timeout))
        try:
            async with asyncio.timeout(timeout):
                while True:
                    latest = await self.client.state()
                    selector_ok = True
                    if resource_id or text is not None:
                        try:
                            node = select(latest, resource_id, text)
                            if resource_id and text is not None:
                                selector_ok = node["text"] == text
                        except TabletError as exc:
                            if exc.code in ("element_not_found", "invalid_target"):
                                selector_ok = False
                            else:
                                raise
                    package_ok = (
                        not package
                        or latest.get("phone_state", {}).get("packageName") == package
                    )
                    explicit = bool(package or resource_id or text is not None)
                    current = fingerprint(latest)
                    if selector_ok and package_ok and (explicit or current == previous):
                        return latest, {
                            "verified": True if explicit else None,
                            "observation": "expected_state"
                            if explicit
                            else "stable_snapshot",
                            "settled": True,
                        }
                    previous = current
                    if time.monotonic() >= deadline:
                        break
                    await asyncio.sleep(0.25)
        except TimeoutError:
            pass
        return latest or {}, {
            "verified": False if package or resource_id or text is not None else None,
            "observation": "wait_timeout",
            "settled": False,
        }

    async def interact(
        self,
        action: str,
        *,
        resource_id: str | None = None,
        text: str | None = None,
        x: int | None = None,
        y: int | None = None,
        value: str | None = None,
        clear: bool = True,
        key_code: int | None = None,
        direction: str = "up",
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
        if wait_timeout <= 0:
            raise TabletError("Wait timeout must be positive", "invalid_argument")
        before = await self.client.state()
        width, height = screen_size(before)
        target = None
        if resource_id or text is not None:
            target = select(before, resource_id, text)
        if action == "tap":
            if target:
                if x is not None or y is not None:
                    raise TabletError(
                        "Use a selector or coordinates, not both", "invalid_argument"
                    )
                b = target["bounds"]
                x, y = (
                    int((b["left"] + b["right"]) / 2),
                    int((b["top"] + b["bottom"]) / 2),
                )
            if x is None or y is None or not (0 <= x < width and 0 <= y < height):
                raise TabletError(
                    f"Tap needs coordinates inside {width}x{height}", "invalid_argument"
                )
            method, params = "tap", {"x": x, "y": y}
        elif action == "swipe":
            coords = (start_x, start_y, end_x, end_y)
            if any(v is not None for v in coords) and not all(
                v is not None for v in coords
            ):
                raise TabletError(
                    "Provide all four swipe coordinates", "invalid_argument"
                )
            if not any(v is not None for v in coords):
                points = {
                    "up": (0.5, 0.75, 0.5, 0.3),
                    "down": (0.5, 0.3, 0.5, 0.75),
                    "left": (0.75, 0.5, 0.25, 0.5),
                    "right": (0.25, 0.5, 0.75, 0.5),
                }
                if direction not in points:
                    raise TabletError(
                        "Direction must be up, down, left, or right", "invalid_argument"
                    )
                a, b, c, d = points[direction]
                start_x, start_y, end_x, end_y = (
                    int(width * a),
                    int(height * b),
                    int(width * c),
                    int(height * d),
                )
            if (
                not (
                    0 <= start_x < width
                    and 0 <= end_x < width
                    and 0 <= start_y < height
                    and 0 <= end_y < height
                )
                or duration <= 0
            ):
                raise TabletError(
                    "Invalid swipe bounds or duration", "invalid_argument"
                )
            method, params = (
                "swipe",
                {
                    "startX": start_x,
                    "startY": start_y,
                    "endX": end_x,
                    "endY": end_y,
                    "duration": duration,
                },
            )
        elif action in ("type", "clear"):
            if action == "type" and value is None:
                raise TabletError("Typing requires value", "invalid_argument")
            if target:
                b = target["bounds"]
                await self.client.call(
                    "tap",
                    {
                        "x": int((b["left"] + b["right"]) / 2),
                        "y": int((b["top"] + b["bottom"]) / 2),
                    },
                )
                await asyncio.sleep(0.3)
                focused = await self.client.state()
            else:
                focused = before
            phone = focused.get("phone_state", {})
            focused_nodes = [
                e for e in elements(focused) if e["focused"] and e["editable"]
            ]
            if not phone.get("isEditable") and not focused_nodes:
                raise TabletError(
                    "No editable field is focused; supply its resource_id",
                    "no_focused_input",
                )
            if (
                target
                and phone.get("focusedElement", {}).get("resourceId")
                != target["resource_id"]
                and not any(
                    e["resource_id"] == target["resource_id"] for e in focused_nodes
                )
            ):
                raise TabletError(
                    "Target field did not receive focus", "verification_failed"
                )
            target = target or (focused_nodes[0] if len(focused_nodes) == 1 else None)
            method = "keyboard/clear" if action == "clear" else "keyboard/input"
            params = (
                {}
                if action == "clear"
                else {
                    "base64_text": base64.b64encode(value.encode()).decode(),
                    "clear": clear,
                }
            )
            if target is None and phone.get("focusedElement", {}).get("resourceId"):
                try:
                    target = select(focused, phone["focusedElement"]["resourceId"])
                except TabletError:
                    pass
            if (
                target is not None
                and expect_text is None
                and (clear or action == "clear")
            ):
                expect_text = "" if action == "clear" else value
                if target:
                    resource_id, text = target["resource_id"], None
        elif action == "key":
            if key_code is None:
                raise TabletError("Supply key_code", "invalid_argument")
            method, params = "keyboard/key", {"key_code": key_code}
        elif action == "global":
            if global_action is None:
                raise TabletError("Supply global_action", "invalid_argument")
            method, params = "global", {"action": global_action}
        else:
            raise TabletError("Unknown interaction action", "invalid_argument")
        response = await self.client.call(method, params)
        after, verification = await self.wait(
            resource_id=resource_id if expect_text is not None else None,
            text=expect_text,
            package=expect_package,
            timeout=wait_timeout,
        )
        if action == "tap" and target and verification["verified"] is None:
            focused = (
                after.get("phone_state", {}).get("focusedElement", {}).get("resourceId")
            )
            if focused and focused == target["resource_id"]:
                verification["verified"] = True
                verification["observation"] = "target_focused"
        return {
            "ok": verification["verified"] is not False,
            "accepted": True,
            "response": response,
            **verification,
            "screen_changed": fingerprint(before) != fingerprint(after),
            "state": compact(after),
        }

    async def app(
        self,
        action: str,
        *,
        package: str | None = None,
        activity: str | None = None,
        stop_before_launch: bool = False,
        deep_link: str | None = None,
        intent_action: str = "android.intent.action.VIEW",
        display_id: int = 0,
        urls: list[str] | None = None,
        wait_timeout: float = 6,
    ) -> dict:
        if wait_timeout <= 0:
            raise TabletError("Wait timeout must be positive", "invalid_argument")
        if action in ("launch", "stop") and not package:
            raise TabletError("Supply package", "invalid_argument")
        if action == "launch":
            method, params = (
                "app",
                {"package": package, "stopBeforeLaunch": stop_before_launch},
            )
            if activity:
                params["activity"] = activity
        elif action == "stop":
            method, params = "app/stop", {"package": package}
        elif action == "deep-link":
            if not deep_link:
                raise TabletError("Supply deep_link", "invalid_argument")
            method, params = (
                "app/deep-link",
                {
                    "deepLink": deep_link,
                    "action": intent_action,
                    "displayId": display_id,
                },
            )
            if package:
                params["package"] = package
        elif action == "install":
            if not urls:
                raise TabletError("Supply at least one APK URL", "invalid_argument")
            method, params = "install", {"urls": urls, "hideOverlay": False}
        else:
            raise TabletError("Unknown app action", "invalid_argument")
        response = await self.client.call(
            method, params, timeout=180 if action == "install" else 45
        )
        state, verification = await self.wait(
            package=package if action == "launch" else None, timeout=wait_timeout
        )
        if action == "stop":
            verification["verified"] = bool(
                isinstance(response, dict) and response.get("uiSuccess")
            )
            verification["observation"] = (
                "force_stop_confirmed"
                if verification["verified"]
                else "best_effort_only"
            )
        if action == "install":
            verification["verified"] = bool(
                isinstance(response, dict) and response.get("overallSuccess")
            )
            verification["observation"] = "installer_result"
        return {
            "ok": verification["verified"] is not False,
            "accepted": True,
            "response": response,
            **verification,
            "state": compact(state),
        }

    async def transfer(
        self,
        action: str,
        *,
        path: str,
        local: str | None = None,
        url: str | None = None,
    ) -> dict:
        if action not in ("list", "upload", "download", "delete", "fetch", "push"):
            raise TabletError("Unknown file action", "invalid_argument")
        params = {"path": path}
        if action in ("upload", "download") and not local:
            raise TabletError("Supply a local path", "invalid_argument")
        if action == "upload":
            data = Path(local).expanduser().read_bytes()
            params["data"] = base64.b64encode(data).decode()
        if action in ("fetch", "push"):
            if not url:
                raise TabletError("Supply URL", "invalid_argument")
            params["url"] = url
        response = await self.client.call("files/" + action, params, timeout=90)
        if action == "download":
            if isinstance(response, str):
                try:
                    response = base64.b64decode(response, validate=True)
                except ValueError:
                    raise TabletError(
                        "Download is not binary or base64", "protocol_error"
                    ) from None
            if not isinstance(response, bytes):
                raise TabletError("Download is not binary or base64", "protocol_error")
            output = Path(local).expanduser().resolve()
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_bytes(response)
            return {
                "ok": True,
                "path": path,
                "local": str(output),
                "bytes": len(response),
                "sha256": hashlib.sha256(response).hexdigest(),
            }
        return {"ok": True, "response": response}
