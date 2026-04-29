package net.rustcore.duel.rating;

import java.util.List;

public final class RatingRequest {

    private RatingRequest() { throw new AssertionError("namespace class"); }

    public record PlayerRating(String uuid, double mu, double sigma) {}

    public record Team(int rank, List<PlayerRating> players) {}

    public record Body(String mode_id, List<Team> teams) {}
}
