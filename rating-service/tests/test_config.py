import pytest
from pydantic import ValidationError

from rating_service.config import Settings


SECRET = "x" * 32


def test_settings_loads_rating_prefixed_env(monkeypatch):
    monkeypatch.setenv("RATING_SHARED_SECRET", SECRET)
    monkeypatch.setenv("RATING_BIND_HOST", "0.0.0.0")
    monkeypatch.setenv("RATING_BIND_PORT", "9000")
    monkeypatch.setenv("RATING_LOG_LEVEL", "debug")
    monkeypatch.setenv("RATING_HMAC_MAX_SKEW_SECONDS", "120")

    settings = Settings()

    assert settings.shared_secret == SECRET
    assert settings.bind_host == "0.0.0.0"
    assert settings.bind_port == 9000
    assert settings.log_level == "debug"
    assert settings.hmac_max_skew_seconds == 120


@pytest.mark.parametrize("shared_secret", ["short", ""])
def test_settings_rejects_short_shared_secret(shared_secret):
    with pytest.raises(ValidationError):
        Settings(shared_secret=shared_secret)


@pytest.mark.parametrize("bind_port", [0, 65536])
def test_settings_rejects_invalid_bind_port(bind_port):
    with pytest.raises(ValidationError):
        Settings(shared_secret=SECRET, bind_port=bind_port)


@pytest.mark.parametrize("hmac_max_skew_seconds", [0, 301])
def test_settings_rejects_invalid_hmac_max_skew_seconds(hmac_max_skew_seconds):
    with pytest.raises(ValidationError):
        Settings(shared_secret=SECRET, hmac_max_skew_seconds=hmac_max_skew_seconds)


def test_settings_rejects_invalid_log_level():
    with pytest.raises(ValidationError):
        Settings(shared_secret=SECRET, log_level="verbose")
