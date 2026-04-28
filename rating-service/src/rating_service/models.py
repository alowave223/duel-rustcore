from typing import Annotated

from pydantic import BaseModel, Field


UUID_LEN = 36


class PlayerRating(BaseModel):
    uuid: Annotated[str, Field(min_length=UUID_LEN, max_length=UUID_LEN)]
    mu: float = 25.0
    sigma: Annotated[float, Field(gt=0.0)] = 25.0 / 3.0


class Team(BaseModel):
    rank: Annotated[int, Field(ge=0)]
    players: Annotated[list[PlayerRating], Field(min_length=1)]


class RateRequest(BaseModel):
    mode_id: Annotated[str, Field(min_length=1, max_length=64)]
    teams: Annotated[list[Team], Field(min_length=2)]


class RatedPlayer(BaseModel):
    uuid: str
    mu_before: float
    sigma_before: float
    mu_after: float
    sigma_after: float
    ordinal_after: float
    delta_ordinal: float


class RateResponse(BaseModel):
    mode_id: str
    model: str = "PlackettLuce"
    players: list[RatedPlayer]


class PredictRequest(BaseModel):
    teams: Annotated[list[Team], Field(min_length=2)]


class PredictResponse(BaseModel):
    win_probabilities: list[float]
    draw_probability: float
