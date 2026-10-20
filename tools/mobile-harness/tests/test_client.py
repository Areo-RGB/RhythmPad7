import base64
import unittest
from unittest.mock import Mock

from rhythmpad_harness.client import HarnessConfig, RhythmPadHarness


class HarnessClientTests(unittest.TestCase):
    def test_status_uses_capabilities_and_phone_state(self):
        harness = object.__new__(RhythmPadHarness)
        harness.device = Mock()
        harness.device.capabilities = {"backend": "local-android-http", "platform": "android"}
        harness.device.ui.return_value = {"phone_state": {"packageName": "com.example"}}
        result = harness.status()
        self.assertTrue(result["ok"])
        self.assertEqual(result["phone_state"]["packageName"], "com.example")

    def test_screenshot_decodes_base64_and_writes_file(self):
        from tempfile import TemporaryDirectory
        from pathlib import Path

        harness = object.__new__(RhythmPadHarness)
        harness.device = Mock()
        payload = b"\x89PNG\r\n\x1a\ntest"
        harness.device.screenshot.return_value = base64.b64encode(payload).decode()
        with TemporaryDirectory() as directory:
            result = harness.screenshot(str(Path(directory) / "screen.png"))
            self.assertEqual(Path(result["path"]).read_bytes(), payload)
            self.assertEqual(result["bytes"], len(payload))

    def test_launch_verifier_accepts_normalized_mobile_harness_state(self):
        harness = object.__new__(RhythmPadHarness)
        harness.device = Mock()
        harness.device.start_app.return_value = None
        harness.device.ui.return_value = {
            "phone_state": {"package_name": "com.rhythmpad7.app"}
        }
        result = harness.launch("com.rhythmpad7.app")
        self.assertTrue(result["ok"])
        self.assertEqual(result["foreground_package"], "com.rhythmpad7.app")
