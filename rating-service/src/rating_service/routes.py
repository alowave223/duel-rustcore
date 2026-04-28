from typing import TypeVar

from fastapi import APIRouter, Header, HTTPException, Request
from pydantic import BaseModel, ValidationError

from rating_service.auth import HmacError, verify
from rating_service.config import Settings
from rating_service.models import PredictRequest, PredictResponse, RateRequest, RateResponse
from rating_service.rating import calculate_new_ratings, predict_win


RequestModel = TypeVar("RequestModel", bound=BaseModel)


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
