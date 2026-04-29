import hashlib
import hmac
import time

import pytest

from rating_service.auth import HmacError, secret_fingerprint, sign, verify


SECRET = "x" * 32


def test_sign_and_verify_roundtrip():
    body = b'{"mode_id":"k","teams":[]}'
    ts = str(int(time.time()))
    sig = sign(SECRET, ts, body)
    verify(SECRET, ts, sig, body, max_skew=60)  # no exception


def test_sign_matches_known_vector():
    body = b'{"mode_id":"k","teams":[]}'
    ts = "1710000000"
    msg = ts.encode("utf-8") + b"." + body
    expected = hmac.new(SECRET.encode("utf-8"), msg, hashlib.sha256).hexdigest()

    assert sign(SECRET, ts, body) == expected


def test_secret_fingerprint_is_stable_and_does_not_expose_secret():
    fingerprint = secret_fingerprint(SECRET)

    assert fingerprint == hashlib.sha256(SECRET.encode("utf-8")).hexdigest()[:12]
    assert SECRET not in fingerprint


def test_verify_rejects_wrong_signature():
    body = b"{}"
    ts = str(int(time.time()))
    with pytest.raises(HmacError):
        verify(SECRET, ts, "0" * 64, body, max_skew=60)


def test_verify_rejects_bad_signature_format():
    body = b"{}"
    ts = str(int(time.time()))
    with pytest.raises(HmacError, match="invalid signature format"):
        verify(SECRET, ts, "deadbeef", body, max_skew=60)


def test_verify_rejects_invalid_timestamp_type():
    body = b"{}"
    sig = sign(SECRET, str(int(time.time())), body)
    with pytest.raises(HmacError):
        verify(SECRET, None, sig, body, max_skew=60)


def test_verify_rejects_invalid_timestamp_value():
    body = b"{}"
    sig = sign(SECRET, str(int(time.time())), body)
    with pytest.raises(HmacError):
        verify(SECRET, "not-a-timestamp", sig, body, max_skew=60)


def test_verify_rejects_stale_timestamp():
    body = b"{}"
    ts = str(int(time.time()) - 600)
    sig = sign(SECRET, ts, body)
    with pytest.raises(HmacError):
        verify(SECRET, ts, sig, body, max_skew=60)


def test_verify_rejects_future_timestamp():
    body = b"{}"
    ts = str(int(time.time()) + 600)
    sig = sign(SECRET, ts, body)
    with pytest.raises(HmacError):
        verify(SECRET, ts, sig, body, max_skew=60)
