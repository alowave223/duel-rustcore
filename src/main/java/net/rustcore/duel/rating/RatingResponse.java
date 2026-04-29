package net.rustcore.duel.rating;

import java.util.List;

public final class RatingResponse {

    private RatingResponse() { throw new AssertionError("namespace class"); }

    public record RatedPlayer(
            String uuid,
            double mu_before,
            double sigma_before,
            double mu_after,
            double sigma_after,
            double ordinal_after,
            double delta_ordinal
    ) {}

    public record Body(String mode_id, String model, List<RatedPlayer> players) {}
}
