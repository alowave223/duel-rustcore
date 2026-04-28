import pytest
from pydantic import ValidationError
from rating_service.models import (
    PlayerRating,
    PredictRequest,
    PredictResponse,
    RatedPlayer,
    RateRequest,
    RateResponse,
    Team,
)


def test_rate_request_minimal_1v1():
    req = RateRequest(
        mode_id="kitbuilder",
        teams=[
            Team(rank=0, players=[PlayerRating(uuid="a"*36, mu=25.0, sigma=8.333)]),
            Team(rank=1, players=[PlayerRating(uuid="b"*36, mu=25.0, sigma=8.333)]),
        ],
    )
    assert len(req.teams) == 2


def test_collection_size_constraints_are_in_json_schema():
    team_schema = Team.model_json_schema()
    rate_request_schema = RateRequest.model_json_schema()

    assert team_schema["properties"]["players"]["minItems"] == 1
    assert rate_request_schema["properties"]["teams"]["minItems"] == 2


def test_player_rating_defaults_mu_and_sigma():
    rating = PlayerRating(uuid="a"*36)

    assert rating.mu == 25.0
    assert rating.sigma == 25.0 / 3.0


def test_rate_request_rejects_single_team():
    with pytest.raises(ValidationError):
        RateRequest(
            mode_id="x",
            teams=[
                Team(rank=0, players=[PlayerRating(uuid="a"*36, mu=25.0, sigma=8.333)])
            ],
        )


def test_team_rejects_empty_players():
    with pytest.raises(ValidationError):
        Team(rank=0, players=[])


def test_player_rating_rejects_negative_sigma():
    with pytest.raises(ValidationError):
        PlayerRating(uuid="a"*36, mu=25.0, sigma=-1.0)


def test_rate_response_defaults_model_with_rated_player():
    player = RatedPlayer(
        uuid="a"*36,
        mu_before=25.0,
        sigma_before=8.333,
        mu_after=26.0,
        sigma_after=8.0,
        ordinal_after=2.0,
        delta_ordinal=1.0,
    )

    response = RateResponse(mode_id="kitbuilder", players=[player])

    assert response.model == "PlackettLuce"
    assert response.players == [player]


def test_predict_models_construct_with_planned_fields():
    teams = [
        Team(rank=0, players=[PlayerRating(uuid="a"*36, mu=25.0, sigma=8.333)]),
        Team(rank=1, players=[PlayerRating(uuid="b"*36, mu=25.0, sigma=8.333)]),
    ]

    request = PredictRequest(teams=teams)
    response = PredictResponse(win_probabilities=[0.6, 0.4], draw_probability=0.0)

    assert request.teams == teams
    assert response.win_probabilities == [0.6, 0.4]
    assert response.draw_probability == 0.0
