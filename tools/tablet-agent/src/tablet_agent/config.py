"""Local device profiles. Never requires credentials in the checkout."""

import json
import os
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlsplit


class TabletError(Exception):
    def __init__(
        self, message: str, code: str = "tablet_error", *, outcome_unknown: bool = False
    ):
        super().__init__(message)
        self.code = code
        self.outcome_unknown = outcome_unknown

    def as_dict(self) -> dict:
        return {
            "ok": False,
            "error": {
                "code": self.code,
                "message": str(self),
                "outcome_unknown": self.outcome_unknown,
            },
        }


def config_path() -> Path:
    override = os.environ.get("TABLET_CONFIG")
    if override:
        return Path(override).expanduser()
    return (
        Path(os.environ.get("XDG_CONFIG_HOME", str(Path.home() / ".config")))
        / "tablet-agent/profiles.json"
    )


def load_config() -> dict:
    path = config_path()
    if not path.exists():
        return {"default": None, "profiles": {}}
    try:
        data = json.loads(path.read_text())
        if not isinstance(data, dict) or not isinstance(data.get("profiles"), dict):
            raise TypeError("Invalid profile document")
        return data
    except (ValueError, TypeError, OSError) as exc:
        raise TabletError(f"Cannot read profiles at {path}", "config_error") from exc


def validate_url(url: str) -> str:
    parts = urlsplit(url)
    if (
        parts.scheme not in ("ws", "wss")
        or not parts.hostname
        or parts.username
        or parts.password
        or parts.query
        or parts.fragment
    ):
        raise TabletError(
            "Use a ws://host:port/ URL without credentials or query parameters",
            "config_error",
        )
    try:
        _ = parts.port
    except ValueError as exc:
        raise TabletError("Invalid WebSocket port", "config_error") from exc
    return url


def save_profile(name: str, url: str, token: str, *, default: bool = False) -> dict:
    if not name.strip() or not token.strip():
        raise TabletError("Profile name and token must not be empty", "config_error")
    validate_url(url)
    data = load_config()
    data["profiles"][name] = {"url": url, "token": token}
    if default or not data.get("default"):
        data["default"] = name
    path = config_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as stream:
        json.dump(data, stream, indent=2)
        stream.write("\n")
    temporary.chmod(0o600)
    temporary.replace(path)
    return {"ok": True, "profile": name, "url": url, "default": data["default"] == name}


def list_profiles() -> dict:
    data = load_config()
    return {
        "ok": True,
        "default": data.get("default"),
        "profiles": {
            name: {"url": value["url"]} for name, value in data["profiles"].items()
        },
    }


@dataclass(frozen=True)
class Device:
    name: str
    url: str
    token: str


def resolve_device(profile: str | None = None) -> Device:
    data = load_config()
    name = profile or os.environ.get("TABLET_PROFILE") or data.get("default")
    saved = data["profiles"].get(name, {})
    if name and not saved and not os.environ.get("TABLET_URL"):
        raise TabletError(f"Unknown profile: {name}", "config_error")
    url = os.environ.get("TABLET_URL") or saved.get("url")
    token = os.environ.get("TABLET_TOKEN") or saved.get("token")
    if not url or not token:
        raise TabletError(
            "Configure a profile with 'tablet profile add', or set TABLET_URL and TABLET_TOKEN",
            "config_error",
        )
    return Device(name or "environment", validate_url(url), token)
