import hashlib
import hmac
import string
import time


class HmacError(Exception):
    pass


def sign(secret: str, timestamp: str, body: bytes) -> str:
    msg = timestamp.encode("utf-8") + b"." + body
    return hmac.new(secret.encode("utf-8"), msg, hashlib.sha256).hexdigest()


def verify(secret: str, timestamp: str, signature: str, body: bytes, max_skew: int) -> None:
    try:
        ts_int = int(timestamp)
    except (TypeError, ValueError) as exc:
        raise HmacError("invalid timestamp") from exc

    now = int(time.time())
    if abs(now - ts_int) > max_skew:
        raise HmacError("timestamp out of window")

    if not isinstance(signature, str) or len(signature) != 64:
        raise HmacError("invalid signature format")
    if any(char not in string.hexdigits for char in signature):
        raise HmacError("invalid signature format")

    expected = sign(secret, timestamp, body)
    if not hmac.compare_digest(expected, signature):
        raise HmacError("signature mismatch")
