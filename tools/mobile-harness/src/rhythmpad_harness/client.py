"""Use the upstream Mobile Harness facade for local Android Portal control."""

from __future__ import annotations

import base64
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from mobilerun_core import Mobilerun


@dataclass(frozen=True)
class HarnessConfig:
    url: str
    token: str


class RhythmPadHarness:
    """High-level, read/act/verify wrapper for the local tablet."""

    def __init__(self, config: HarnessConfig):
        self.config = config
        self.device = Mobilerun().connect(
            backend="local-android-http", url=config.url, token=config.token
        )

    @staticmethod
    def _state(device: Any) -> dict[str, Any]:
        state = device.ui(filter=False)
        return dict(state)

    @staticmethod
    def _foreground_package(state: dict[str, Any]) -> str | None:
        phone_state = state.get("phone_state", {})
        return (
            phone_state.get("package_name")
            or phone_state.get("packageName")
            or phone_state.get("app_id")
        )

    def status(self) -> dict[str, Any]:
        state = self._state(self.device)
        return {
            "ok": True,
            "backend": self.device.capabilities.get("backend"),
            "platform": self.device.capabilities.get("platform"),
            "capabilities": self.device.capabilities,
            "phone_state": state.get("phone_state", {}),
        }

    def inspect(self) -> dict[str, Any]:
        state = self._state(self.device)
        nodes = self.device.find_nodes(tree=state.get("a11y_tree"), any_contains="")
        return {
            "ok": True,
            "phone_state": state.get("phone_state", {}),
            "node_count": len(nodes),
            "nodes": nodes,
        }

    def screenshot(self, output: str) -> dict[str, Any]:
        encoded = self.device.screenshot(hide_overlay=True)
        data = base64.b64decode(encoded, validate=True)
        path = Path(output).expanduser().resolve()
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        return {"ok": True, "path": str(path), "bytes": len(data), "mime_type": "image/png"}

    def tap(self, *, text: str | None = None, resource_id: str | None = None,
            x: int | None = None, y: int | None = None) -> dict[str, Any]:
        if text is not None and any(value is not None for value in (resource_id, x, y)):
            raise ValueError("Use text, resource_id, or coordinates, not combinations")
        if text is not None:
            self.device.tap_text(text)
            target = {"text": text}
        elif resource_id is not None:
            matches = self.device.find_nodes(resource_id=resource_id)
            if len(matches) != 1:
                raise ValueError(f"resource_id matched {len(matches)} nodes")
            self.device.tap_node(matches[0])
            target = {"resource_id": resource_id}
        elif x is not None and y is not None:
            self.device.tap(x, y)
            target = {"x": x, "y": y}
        else:
            raise ValueError("Supply text, resource_id, or both x and y")
        return {"ok": True, "accepted": True, "target": target}

    def type(self, value: str, *, text: str | None = None,
             resource_id: str | None = None, clear: bool = True) -> dict[str, Any]:
        if text is not None:
            self.device.tap_text(text)
        elif resource_id is not None:
            matches = self.device.find_nodes(resource_id=resource_id)
            if len(matches) != 1:
                raise ValueError(f"resource_id matched {len(matches)} nodes")
            self.device.tap_node(matches[0])
        self.device.type(value, clear=clear)
        return {"ok": True, "accepted": True, "value": value, "clear": clear}

    def launch(self, package: str, activity: str | None = None) -> dict[str, Any]:
        self.device.start_app(package, activity=activity)
        state = self._state(self.device)
        foreground = self._foreground_package(state)
        return {"ok": foreground == package, "accepted": True,
                "foreground_package": foreground, "expected_package": package}

    def diagnostics(self) -> dict[str, Any]:
        status = self.status()
        screenshot = self.device.screenshot(hide_overlay=True)
        packages = self.device.list_apps()
        return {**status, "screenshot_ok": bool(screenshot),
                "launchable_package_count": len(packages)}
