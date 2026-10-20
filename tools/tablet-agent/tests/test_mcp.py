"""Exercise real stdio MCP transport against a fake local Portal."""

import base64
import json
import os
import sys
import unittest

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client
from websockets.asyncio.server import serve

PNG = b"\x89PNG\r\n\x1a\n" + b"test"


class MCPTests(unittest.IsolatedAsyncioTestCase):
    async def test_stdio_tools_and_native_screenshot(self):
        async def handler(ws):
            async for raw in ws:
                request = json.loads(raw)
                method = request["method"]
                result = {
                    "version": "test-version",
                    "state": {
                        "a11y_tree": [
                            {
                                "boundsInScreen": {
                                    "left": 0,
                                    "top": 0,
                                    "right": 800,
                                    "bottom": 600,
                                }
                            }
                        ],
                        "phone_state": {"packageName": "test"},
                    },
                    "screenshot": base64.b64encode(PNG).decode(),
                }.get(method)
                if method == "bad":
                    await ws.send(
                        json.dumps(
                            {
                                "id": request["id"],
                                "status": "error",
                                "error": "bad request",
                            }
                        )
                    )
                else:
                    await ws.send(
                        json.dumps(
                            {"id": request["id"], "status": "success", "result": result}
                        )
                    )

        async with serve(handler, "127.0.0.1", 0) as server:
            port = server.sockets[0].getsockname()[1]
            params = StdioServerParameters(
                command=sys.executable,
                args=["-c", "from tablet_agent.server import main; main()"],
                env={
                    **os.environ,
                    "TABLET_URL": f"ws://127.0.0.1:{port}/",
                    "TABLET_TOKEN": "test",
                    "TABLET_PROFILE": "",
                },
            )
            async with (
                stdio_client(params) as (read, write),
                ClientSession(read, write) as session,
            ):
                await session.initialize()
                tools = await session.list_tools()
                self.assertEqual(len(tools.tools), 11)
                result = await session.call_tool("device_status", {})
                self.assertFalse(result.isError)
                data = json.loads(result.content[0].text)
                self.assertEqual(data["version"], "test-version")
                result = await session.call_tool("inspect_screen", {})
                self.assertFalse(result.isError)
                self.assertEqual(
                    json.loads(result.content[0].text)["state"]["width"], 800
                )
                result = await session.call_tool("take_screenshot", {})
                self.assertFalse(result.isError)
                self.assertEqual(result.content[0].type, "image")
                self.assertEqual(base64.b64decode(result.content[0].data), PNG)
                result = await session.call_tool("rpc", {"method": "bad"})
                self.assertTrue(result.isError)
                self.assertIn("rpc_error", result.content[0].text)
