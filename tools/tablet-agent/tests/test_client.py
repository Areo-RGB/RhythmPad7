import asyncio
import base64
import json
import unittest

from tablet_agent.client import PortalClient
from tablet_agent.config import Device, TabletError
from websockets.asyncio.server import serve

PNG = b"\x89PNG\r\n\x1a\n" + b"test image payload"


class ClientTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.requests = []
        self.mode = "events"
        self.connections = 0

        async def handler(ws):
            self.connections += 1
            self.assertIn("token=secret", ws.request.path)
            async for raw in ws:
                if raw == "PONG":
                    continue
                request = json.loads(raw)
                self.requests.append(request)
                if self.mode == "timeout":
                    await asyncio.sleep(0.2)
                    continue
                if self.mode == "disconnect":
                    await ws.close()
                    return
                if self.mode == "malformed":
                    await ws.send("not json")
                    continue
                if self.mode == "error":
                    await ws.send(
                        json.dumps(
                            {
                                "id": request["id"],
                                "status": "error",
                                "error": "bad secret",
                            }
                        )
                    )
                    continue
                await ws.send("PING")
                await ws.send(json.dumps({"type": "device_event", "payload": {}}))
                await ws.send(
                    json.dumps({"id": "old-id", "status": "success", "result": "wrong"})
                )
                if self.mode == "binary":
                    await ws.send(b"not-the-right-id".ljust(36, b" ") + b"wrong")
                    await ws.send(request["id"].encode() + PNG)
                    continue
                value = (
                    {"a11y_tree": [], "phone_state": {}}
                    if request["method"] == "state"
                    else "correct"
                )
                if self.mode == "string_state":
                    value = json.dumps(value)
                if self.mode == "base64":
                    value = base64.b64encode(PNG).decode()
                await ws.send(
                    json.dumps(
                        {"id": request["id"], "status": "success", "result": value}
                    )
                )

        self.server = await serve(handler, "127.0.0.1", 0)
        port = self.server.sockets[0].getsockname()[1]
        self.device = Device("test", f"ws://127.0.0.1:{port}/", "secret")

    async def asyncTearDown(self):
        self.server.close()
        await self.server.wait_closed()

    async def test_events_and_unrelated_replies_are_not_results(self):
        async with PortalClient(self.device) as client:
            self.assertEqual(await client.call("version"), "correct")
            self.assertEqual(len(client.events), 2)
            self.assertGreaterEqual(await client.ping(), 0)

    async def test_binary_request_id_and_png(self):
        self.mode = "binary"
        async with PortalClient(self.device) as client:
            self.assertEqual(await client.screenshot(), PNG)

    async def test_base64_png(self):
        self.mode = "base64"
        async with PortalClient(self.device) as client:
            self.assertEqual(await client.screenshot(), PNG)

    async def test_string_state(self):
        self.mode = "string_state"
        async with PortalClient(self.device) as client:
            self.assertEqual((await client.state())["a11y_tree"], [])

    async def test_error_is_redacted(self):
        self.mode = "error"
        async with PortalClient(self.device) as client:
            with self.assertRaises(TabletError) as error:
                await client.call("tap")
            self.assertNotIn("secret", str(error.exception))
            self.assertEqual(error.exception.code, "rpc_error")

    async def test_timeout_does_not_retry_mutation(self):
        self.mode = "timeout"
        async with PortalClient(self.device, timeout=0.05) as client:
            with self.assertRaises(TabletError) as error:
                await client.call("tap")
            self.assertTrue(error.exception.outcome_unknown)
        self.assertEqual(len(self.requests), 1)
        self.assertEqual(self.connections, 1)

    async def test_disconnect_reports_unknown_outcome(self):
        self.mode = "disconnect"
        async with PortalClient(self.device) as client:
            with self.assertRaises(TabletError) as error:
                await client.call("tap")
            self.assertTrue(error.exception.outcome_unknown)

    async def test_malformed_response(self):
        self.mode = "malformed"
        async with PortalClient(self.device) as client:
            with self.assertRaises(TabletError) as error:
                await client.call("version")
            self.assertEqual(error.exception.code, "protocol_error")

    async def test_concurrent_calls_are_serialized(self):
        async with PortalClient(self.device) as client:
            results = await asyncio.gather(client.call("version"), client.call("time"))
            self.assertEqual(results, ["correct", "correct"])
