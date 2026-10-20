"""Serialized request/response transport; unsolicited events never count as replies."""

import asyncio
import base64
import json
import uuid
from collections import deque
from urllib.parse import urlencode

from websockets.asyncio.client import connect
from websockets.exceptions import WebSocketException

from .config import Device, TabletError


class PortalClient:
    def __init__(self, device: Device, timeout: float = 20):
        if timeout <= 0:
            raise TabletError("Timeout must be positive", "invalid_argument")
        self.device = device
        self.timeout = timeout
        self.ws = None
        self.events = deque(maxlen=50)
        self._lock = asyncio.Lock()

    async def __aenter__(self):
        try:
            self.ws = await connect(
                self.device.url
                + ("&" if "?" in self.device.url else "?")
                + urlencode({"token": self.device.token}),
                proxy=None,
                open_timeout=min(self.timeout, 10),
                close_timeout=2,
                max_size=150 * 1024 * 1024,
            )
        except (OSError, TimeoutError, WebSocketException) as exc:
            # Upstream exceptions may include the authenticated URL.
            raise TabletError(
                f"Cannot connect to {self.device.url} ({type(exc).__name__})",
                "connection_failed",
            ) from None
        return self

    async def __aexit__(self, *_):
        if self.ws is not None:
            await self.ws.close()

    async def ping(self) -> float:
        async with self._lock:
            try:
                async with asyncio.timeout(self.timeout):
                    waiter = await self.ws.ping()
                    return await waiter
            except (TimeoutError, WebSocketException, OSError) as exc:
                raise TabletError(
                    f"WebSocket ping failed ({type(exc).__name__})", "connection_failed"
                ) from None

    async def call(
        self, method: str, params: dict | None = None, *, timeout: float | None = None
    ):
        if params is not None and not isinstance(params, dict):
            raise TabletError("RPC params must be an object", "invalid_argument")
        if self.ws is None:
            raise TabletError("Client is not connected", "connection_failed")
        ident = str(uuid.uuid4())
        sent = False
        async with self._lock:
            try:
                async with asyncio.timeout(timeout or self.timeout):
                    await self.ws.send(
                        json.dumps(
                            {"id": ident, "method": method, "params": params or {}}
                        )
                    )
                    sent = True
                    while True:
                        raw = await self.ws.recv()
                        if isinstance(raw, bytes):
                            if raw[:36] == ident.encode():
                                return raw[36:]
                            continue
                        if raw == "PING":
                            await self.ws.send("PONG")
                            continue
                        if raw == "PONG":
                            continue
                        try:
                            response = json.loads(raw)
                        except ValueError:
                            raise TabletError(
                                "Portal sent invalid JSON",
                                "protocol_error",
                                outcome_unknown=sent,
                            ) from None
                        if not isinstance(response, dict):
                            raise TabletError(
                                "Portal response must be an object",
                                "protocol_error",
                                outcome_unknown=sent,
                            )
                        if response.get("id") != ident:
                            self.events.append(response)
                            continue
                        if response.get("status") != "success":
                            message = str(
                                response.get("error", "Portal rejected the request")
                            )
                            message = message.replace(self.device.token, "[redacted]")
                            raise TabletError(message, "rpc_error")
                        return response.get("result")
            except (TimeoutError, WebSocketException, OSError) as exc:
                # Mutations aren't retried: a lost reply doesn't mean the device did nothing.
                await self.ws.close()
                raise TabletError(
                    f"{method} did not finish ({type(exc).__name__})",
                    "request_failed",
                    outcome_unknown=sent,
                ) from None

    async def state(self) -> dict:
        result = await self.call("state", {"filter": False})
        if isinstance(result, str):
            try:
                result = json.loads(result)
            except ValueError:
                raise TabletError("State is not valid JSON", "protocol_error") from None
        if not isinstance(result, dict) or "a11y_tree" not in result:
            raise TabletError("State has no accessibility tree", "protocol_error")
        return result

    async def screenshot(self, hide_overlay: bool = True) -> bytes:
        result = await self.call("screenshot", {"hideOverlay": hide_overlay})
        if isinstance(result, str):
            try:
                result = base64.b64decode(result, validate=True)
            except ValueError:
                raise TabletError(
                    "Invalid screenshot base64", "protocol_error"
                ) from None
        if not isinstance(result, bytes) or not result.startswith(b"\x89PNG\r\n\x1a\n"):
            raise TabletError("Screenshot response is not PNG", "protocol_error")
        return result
