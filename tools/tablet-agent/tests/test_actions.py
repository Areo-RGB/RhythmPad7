import copy
import unittest
from unittest.mock import AsyncMock

from tablet_agent.actions import Tablet, compact, select
from tablet_agent.config import TabletError
from tablet_agent.diagnostics import doctor


def state(text="old", focused=True):
    return {
        "a11y_tree": [
            {
                "boundsInScreen": {"left": 0, "top": 0, "right": 1000, "bottom": 800},
                "children": [
                    {
                        "resourceId": "test:id/input",
                        "text": text,
                        "isEditable": True,
                        "isFocused": focused,
                        "boundsInScreen": {
                            "left": 100,
                            "top": 200,
                            "right": 300,
                            "bottom": 260,
                        },
                    }
                ],
            }
        ],
        "phone_state": {
            "packageName": "test",
            "isEditable": focused,
            "focusedElement": {"resourceId": "test:id/input" if focused else ""},
        },
    }


class SelectorTests(unittest.TestCase):
    def test_short_and_full_resource_id(self):
        self.assertEqual(select(state(), "input")["text"], "old")
        self.assertEqual(select(state(), "test:id/input")["text"], "old")

    def test_ambiguous_text_rejected(self):
        value = state()
        second = copy.deepcopy(value["a11y_tree"][0]["children"][0])
        second["resourceId"] = "test:id/other"
        value["a11y_tree"][0]["children"].append(second)
        with self.assertRaises(TabletError) as error:
            select(value, text="old")
        self.assertEqual(error.exception.code, "ambiguous_selector")

    def test_hidden_element_ignored(self):
        value = state()
        value["a11y_tree"][0]["children"][0]["isVisibleToUser"] = False
        with self.assertRaises(TabletError):
            select(value, "input")

    def test_empty_expectation_does_not_match_empty_description(self):
        with self.assertRaises(TabletError):
            select(state("still not empty"), "input", "")

    def test_compact_includes_dimensions(self):
        self.assertEqual(compact(state())["width"], 1000)


class ActionTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.client = AsyncMock()
        self.client.state.return_value = state()
        self.client.call.return_value = "accepted"
        self.tablet = Tablet(self.client)

    async def test_tap_resolves_current_bounds(self):
        result = await self.tablet.interact("tap", resource_id="input")
        self.client.call.assert_awaited_once_with("tap", {"x": 200, "y": 230})
        self.assertTrue(result["verified"])

    async def test_raw_tap_does_not_claim_effect(self):
        result = await self.tablet.interact("tap", x=500, y=500)
        self.assertIsNone(result["verified"])
        self.assertFalse(result["screen_changed"])

    async def test_out_of_bounds_fails_before_rpc(self):
        with self.assertRaises(TabletError):
            await self.tablet.interact("tap", x=1001, y=1)
        self.client.call.assert_not_awaited()

    async def test_typing_without_focused_field_fails(self):
        self.client.state.return_value = state(focused=False)
        with self.assertRaises(TabletError) as error:
            await self.tablet.interact("type", value="new")
        self.assertEqual(error.exception.code, "no_focused_input")
        self.client.call.assert_not_awaited()

    async def test_clear_verifies_empty_string(self):
        self.client.state.side_effect = [state(), state(), state("")]
        result = await self.tablet.interact("clear", resource_id="input")
        self.assertTrue(result["verified"])
        self.assertEqual(result["observation"], "expected_state")

    async def test_wait_failure_is_not_success(self):
        result = await self.tablet.interact(
            "tap", x=1, y=1, expect_package="missing", wait_timeout=0.1
        )
        self.assertFalse(result["ok"])
        self.assertTrue(result["accepted"])
        self.assertFalse(result["verified"])

    async def test_clear_success_without_empty_value_fails_verification(self):
        result = await self.tablet.interact(
            "clear", resource_id="input", wait_timeout=0.1
        )
        self.assertFalse(result["ok"])
        self.assertFalse(result["verified"])

    async def test_best_effort_stop_is_not_verified(self):
        self.client.call.return_value = {"overallSuccess": True, "uiSuccess": False}
        result = await self.tablet.app("stop", package="test")
        self.assertFalse(result["verified"])
        self.assertEqual(result["observation"], "best_effort_only")

    async def test_install_uses_installer_result(self):
        self.client.call.return_value = {"overallSuccess": False}
        result = await self.tablet.app("install", urls=["https://example.com/test.apk"])
        self.assertFalse(result["ok"])

    async def test_readonly_diagnostics_has_no_mutations(self):
        self.client.ping.return_value = 0.01
        self.client.device.name = "test"
        self.client.device.url = "ws://localhost/"
        self.client.call.side_effect = ["1", [], 123, {"enabled": True}, {"files": []}]
        self.client.screenshot.return_value = b"\x89PNG\r\n\x1a\n"
        result = await doctor(self.client)
        self.assertTrue(result["ok"])
        methods = [call.args[0] for call in self.client.call.await_args_list]
        self.assertEqual(
            methods,
            ["version", "packages", "time", "screen/keepAwake/status", "files/list"],
        )


class DiagnosticCleanupTests(unittest.IsolatedAsyncioTestCase):
    async def test_failed_roundtrip_still_restores_originals(self):
        from unittest.mock import patch

        client = AsyncMock()
        client.timeout = 1
        client.ping.return_value = 0.01
        client.state.return_value = state()
        client.screenshot.return_value = b"\x89PNG\r\n\x1a\n"
        client.device.name = "test"
        client.device.url = "ws://localhost/"
        clipboard_value = "original clipboard"
        awake_value = True

        async def call(method, params=None):
            nonlocal clipboard_value, awake_value
            if method == "version":
                return "1"
            if method == "packages":
                return []
            if method == "time":
                return 123
            if method == "screen/keepAwake/status":
                return {"enabled": awake_value}
            if method == "screen/keepAwake/set":
                awake_value = params["enabled"]
                return {"enabled": awake_value}
            if method == "clipboard/get":
                return clipboard_value
            if method == "clipboard/set":
                clipboard_value = params["text"]
                return "ok"
            if method == "files/download":
                raise TabletError(
                    "test disconnection", "request_failed", outcome_unknown=True
                )
            return "ok"

        client.call.side_effect = call
        clean_client = AsyncMock()
        clean_client.call.side_effect = call
        manager = AsyncMock()
        manager.__aenter__.return_value = clean_client
        with patch("tablet_agent.diagnostics.PortalClient", return_value=manager):
            result = await doctor(client, full=True)
        self.assertFalse(result["ok"])
        self.assertTrue(all(item["ok"] for item in result["cleanup"]))
        self.assertEqual(clipboard_value, "original clipboard")
        self.assertTrue(awake_value)
        self.assertEqual(clean_client.call.await_args_list[0].args[0], "files/delete")
