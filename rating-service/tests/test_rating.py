import pytest

from rating_service.models import PlayerRating, RateRequest, Team
from rating_service.rating import calculate_new_ratings, predict_win


def test_winner_mu_increases_loser_decreases():
    req = RateRequest(
        mode_id="kitbuilder",
        teams=[
            Team(rank=0, players=[PlayerRating(uuid="a" * 36)]),
            Team(rank=1, players=[PlayerRating(uuid="b" * 36)]),
        ],
    )
    resp = calculate_new_ratings(req)
    by_uuid = {p.uuid: p for p in resp.players}
    assert by_uuid["a" * 36].mu_after > 25.0
    assert by_uuid["b" * 36].mu_after < 25.0
    assert by_uuid["a" * 36].delta_ordinal > 0
    assert by_uuid["b" * 36].delta_ordinal < 0


def test_sigma_decreases_after_match():
    req = RateRequest(
        mode_id="kitbuilder",
        teams=[
            Team(rank=0, players=[PlayerRating(uuid="a" * 36)]),
            Team(rank=1, players=[PlayerRating(uuid="b" * 36)]),
        ],
    )
    resp = calculate_new_ratings(req)
    for p in resp.players:
        assert p.sigma_after < p.sigma_before


def test_draw_keeps_mu_close_but_lowers_sigma():
    req = RateRequest(
        mode_id="kitbuilder",
        teams=[
            Team(rank=0, players=[PlayerRating(uuid="a" * 36)]),
            Team(rank=0, players=[PlayerRating(uuid="b" * 36)]),
        ],
    )
    resp = calculate_new_ratings(req)
    for p in resp.players:
        assert abs(p.mu_after - 25.0) < 0.5
        assert p.sigma_after < p.sigma_before


def test_calculate_new_ratings_preserves_all_multiplayer_uuids_once():
    req = RateRequest(
        mode_id="kitbuilder",
        teams=[
            Team(
                rank=0,
                players=[
                    PlayerRating(uuid="a" * 36),
                    PlayerRating(uuid="b" * 36),
                ],
            ),
            Team(
                rank=1,
                players=[
                    PlayerRating(uuid="c" * 36),
                    PlayerRating(uuid="d" * 36),
                ],
            ),
        ],
    )

    resp = calculate_new_ratings(req)

    assert len(resp.players) == 4
    assert sorted(p.uuid for p in resp.players) == sorted(
        ["a" * 36, "b" * 36, "c" * 36, "d" * 36]
    )
    assert len({p.uuid for p in resp.players}) == 4


def test_predict_win_rejects_fewer_than_two_teams():
    with pytest.raises(ValueError, match="at least two teams"):
        predict_win([Team(rank=0, players=[PlayerRating(uuid="a" * 36)])])


def test_predict_win_sums_to_one_with_bounds_and_symmetry():
    teams = [
        Team(rank=0, players=[PlayerRating(uuid="a" * 36)]),
        Team(rank=0, players=[PlayerRating(uuid="b" * 36)]),
    ]
    probs, draw = predict_win(teams)
    assert abs(sum(probs) + draw - 1.0) < 1e-6
    assert len(probs) == len(teams)
    assert all(0.0 <= p <= 1.0 for p in probs)
    assert 0.0 <= draw <= 1.0
    assert abs(probs[0] - probs[1]) < 1e-6
