import hashlib
import logging
import time
from typing import TypeVar

from fastapi import APIRouter, Header, HTTPException, Request
from pydantic import BaseModel, ValidationError

from rating_service.auth import HmacError, secret_fingerprint, verify
from rating_service.config import Settings
from rating_service.models import PredictRequest, PredictResponse, RateRequest, RateResponse
from rating_service.rating import calculate_new_ratings, predict_win


RequestModel = TypeVar("RequestModel", bound=BaseModel)

logger = logging.getLogger("rating_service.auth")


def _parse_request(model: type[RequestModel], body: bytes) -> RequestModel:
    try:
        return model.model_validate_json(body)
    except ValidationError as exc:
        raise HTTPException(status_code=422, detail="invalid request body") from exc


def build_router(settings: Settings) -> APIRouter:
    router = APIRouter()

    @router.get("/healthz")
    async def healthz() -> dict[str, str]:
        return {"status": "ok"}

    async def _check_auth(
        request: Request,
        x_rating_timestamp: str | None,
        x_rating_signature: str | None,
    ) -> bytes:
        if not x_rating_timestamp or not x_rating_signature:
            raise HTTPException(status_code=401, detail="missing auth headers")

        body = await request.body()
        try:
            verify(
                settings.shared_secret,
                x_rating_timestamp,
                x_rating_signature,
                body,
                settings.hmac_max_skew_seconds,
            )
        except HmacError as exc:
            try:
                skew = int(time.time()) - int(x_rating_timestamp or "0")
            except ValueError:
                skew = None
            logger.warning(
                "rating auth failed reason=%s secret_len=%s secret_fp=%s timestamp=%s skew_seconds=%s "
                "body_sha256=%s signature_prefix=%s",
                exc,
                len(settings.shared_secret),
                secret_fingerprint(settings.shared_secret),
                x_rating_timestamp,
                skew,
                hashlib.sha256(body).hexdigest()[:12],
                (x_rating_signature or "")[:12],
            )
            raise HTTPException(status_code=401, detail=str(exc)) from exc
        return body

    @router.post("/v1/rate", response_model=RateResponse)
    async def rate(
        request: Request,
        x_rating_timestamp: str | None = Header(default=None),
        x_rating_signature: str | None = Header(default=None),
    ) -> RateResponse:
        body = await _check_auth(request, x_rating_timestamp, x_rating_signature)
        req = _parse_request(RateRequest, body)
        return calculate_new_ratings(req)

    @router.post("/v1/predict", response_model=PredictResponse)
    async def predict(
        request: Request,
        x_rating_timestamp: str | None = Header(default=None),
        x_rating_signature: str | None = Header(default=None),
    ) -> PredictResponse:
        body = await _check_auth(request, x_rating_timestamp, x_rating_signature)
        req = _parse_request(PredictRequest, body)
        probs, draw = predict_win(req.teams)
        return PredictResponse(win_probabilities=probs, draw_probability=draw)

    return router
