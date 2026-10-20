import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tablet_agent.config import TabletError, list_profiles, resolve_device, save_profile


class ConfigTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.path = Path(self.directory.name) / "profiles.json"
        self.env = patch.dict(os.environ, {"TABLET_CONFIG": str(self.path)}, clear=True)
        self.env.start()

    def tearDown(self):
        self.env.stop()
        self.directory.cleanup()

    def test_save_resolve_and_redact_list(self):
        save_profile("pad", "ws://127.0.0.1:8081/", "secret")
        self.assertEqual(resolve_device().token, "secret")
        self.assertNotIn("secret", json.dumps(list_profiles()))
        self.assertEqual(self.path.stat().st_mode & 0o777, 0o600)

    def test_env_override(self):
        os.environ.update(TABLET_URL="ws://127.0.0.1:8081/", TABLET_TOKEN="env-token")
        self.assertEqual(resolve_device().token, "env-token")

    def test_reject_token_in_url(self):
        with self.assertRaises(TabletError):
            save_profile("pad", "ws://localhost/?token=oops", "secret")

    def test_unknown_profile(self):
        with self.assertRaises(TabletError):
            resolve_device("missing")
