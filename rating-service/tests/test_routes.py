import time

from fastapi.testclient import TestClient

from rating_service.auth import sign
from rating_service.config import Settings
from rating_service.main import create_app


SECRET = "x" * 32


def _client() -> TestClient:
    return TestClient(create_app(Settings(shared_secret=SECRET)))


def _rate_body() -> bytes:
    return (
        b'{"mode_id":"kitbuilder","teams":['
        b'{"rank":0,"players":[{"uuid":"' + b"a" * 36 + b'","mu":25.0,"sigma":8.333}]},'
        b'{"rank":1,"players":[{"uuid":"' + b"b" * 36 + b'","mu":25.0,"sigma":8.333}]}'
        b"]}"
    )


def _predict_body() -> bytes:
    return (
        b'{"teams":['
        b'{"rank":0,"players":[{"uuid":"' + b"a" * 36 + b'","mu":30.0,"sigma":7.0}]},'
        b'{"rank":1,"players":[{"uuid":"' + b"b" * 36 + b'","mu":20.0,"sigma":7.0}]}'
        b"]}"
    )


def _signed_headers(body: bytes) -> dict[str, str]:
    ts = str(int(time.time()))
    return {
        "X-Rating-Timestamp": ts,
        "X-Rating-Signature": sign(SECRET, ts, body),
        "Content-Type": "application/json",
    }


def test_healthz_no_auth() -> None:
    r = _client().get("/healthz")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"


def test_openapi_disabled() -> None:
    r = _client().get("/openapi.json")
    assert r.status_code == 404


def test_rate_endpoint_returns_updated_ratings() -> None:
    body = _rate_body()
    r = _client().post("/v1/rate", content=body, headers=_signed_headers(body))
    assert r.status_code == 200
    payload = r.json()
    assert payload["mode_id"] == "kitbuilder"
    assert len(payload["players"]) == 2


def test_rate_endpoint_rejects_signed_malformed_json_with_422() -> None:
    body = b'{"mode_id":"kitbuilder","teams":['
    r = _client().post("/v1/rate", content=body, headers=_signed_headers(body))
    assert r.status_code == 422


def test_rate_endpoint_rejects_signed_schema_invalid_json_with_422() -> None:
    body = b'{"mode_id":"kitbuilder","teams":[]}'
    r = _client().post("/v1/rate", content=body, headers=_signed_headers(body))
    assert r.status_code == 422


def test_rate_endpoint_rejects_missing_signature() -> None:
    r = _client().post("/v1/rate", content=_rate_body(), headers={"Content-Type": "application/json"})
    assert r.status_code == 401


def test_rate_endpoint_rejects_bad_signature() -> None:
    body = _rate_body()
    headers = _signed_headers(body)
    headers["X-Rating-Signature"] = "0" * 64
    r = _client().post("/v1/rate", content=body, headers=headers)
    assert r.status_code == 401


def test_predict_endpoint_returns_probabilities() -> None:
    body = _predict_body()
    r = _client().post("/v1/predict", content=body, headers=_signed_headers(body))
    assert r.status_code == 200
    payload = r.json()
    assert len(payload["win_probabilities"]) == 2
    assert 0.0 <= payload["draw_probability"] <= 1.0


def test_predict_endpoint_rejects_missing_signature() -> None:
    r = _client().post("/v1/predict", content=_predict_body(), headers={"Content-Type": "application/json"})
    assert r.status_code == 401
