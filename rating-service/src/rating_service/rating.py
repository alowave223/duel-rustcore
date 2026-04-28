from openskill.models import PlackettLuce

from rating_service.models import (
    PlayerRating,
    RateRequest,
    RateResponse,
    RatedPlayer,
    Team,
)


_model = PlackettLuce()


def _to_rating(p: PlayerRating):
    return _model.rating(mu=p.mu, sigma=p.sigma, name=p.uuid)


def calculate_new_ratings(req: RateRequest) -> RateResponse:
    teams_in = [[_to_rating(p) for p in t.players] for t in req.teams]
    ranks = [t.rank for t in req.teams]

    teams_out = _model.rate(teams_in, ranks=ranks)

    rated: list[RatedPlayer] = []
    for team_in, team_out, team_meta in zip(teams_in, teams_out, req.teams):
        for r_in, r_out, p_meta in zip(team_in, team_out, team_meta.players):
            ord_before = r_in.ordinal()
            ord_after = r_out.ordinal()
            rated.append(
                RatedPlayer(
                    uuid=p_meta.uuid,
                    mu_before=r_in.mu,
                    sigma_before=r_in.sigma,
                    mu_after=r_out.mu,
                    sigma_after=r_out.sigma,
                    ordinal_after=ord_after,
                    delta_ordinal=ord_after - ord_before,
                )
            )

    return RateResponse(mode_id=req.mode_id, players=rated)


def predict_win(teams: list[Team]) -> tuple[list[float], float]:
    if len(teams) < 2:
        raise ValueError("predict_win requires at least two teams")

    teams_in = [[_to_rating(p) for p in t.players] for t in teams]
    win_probs = _model.predict_win(teams=teams_in)
    draw_prob = _model.predict_draw(teams=teams_in)
    return [float(p) * (1.0 - draw_prob) for p in win_probs], float(draw_prob)
