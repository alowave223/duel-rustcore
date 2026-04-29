package net.rustcore.duel.rating;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.stats.PlayerStats;
import net.rustcore.duel.stats.StatsManager;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class RatingService {

    public record TeamOutcome(int rank, List<UUID> players) {}

    private final DuelsPlugin plugin;
    private final RatingClient client;
    private final RatingConfig cfg;

    public RatingService(DuelsPlugin plugin, RatingConfig cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.client = new RatingClient(cfg, plugin.getLogger());
    }

    public boolean isEnabled() { return cfg.enabled(); }

    /** Must be called on the Bukkit main thread. */
    public void recordMatch(String modeId, List<TeamOutcome> outcomes) {
        if (!cfg.enabled()) return;
        StatsManager stats = plugin.getStatsManager();

        List<RatingRequest.Team> teams = new ArrayList<>();
        for (TeamOutcome t : outcomes) {
            List<RatingRequest.PlayerRating> ps = new ArrayList<>();
            for (UUID u : t.players()) {
                PlayerStats s = stats.getStats(modeId, u).snapshot();
                ps.add(new RatingRequest.PlayerRating(u.toString(), s.getMu(), s.getSigma()));
            }
            teams.add(new RatingRequest.Team(t.rank(), ps));
        }

        RatingRequest.Body body = new RatingRequest.Body(modeId, teams);

        client.rate(body).whenComplete((resp, err) -> {
            if (err != null) {
                Throwable cause = err.getCause() != null ? err.getCause() : err;
                plugin.getLogger().warning("rating call failed: " + cause);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> apply(modeId, resp));
        });
    }

    private void apply(String modeId, RatingResponse.Body resp) {
        StatsManager stats = plugin.getStatsManager();
        for (RatingResponse.RatedPlayer p : resp.players()) {
            UUID uuid = UUID.fromString(p.uuid());
            Optional<PlayerStats> opt = stats.findStats(modeId, uuid);
            if (opt.isEmpty()) {
                plugin.getLogger().warning("rating apply skipped for offline player " + uuid + " (stats not in cache)");
                continue;
            }
            PlayerStats s = opt.get();
            s.setRating(p.mu_after(), p.sigma_after(), p.ordinal_after());
            s.setMatchesRated(s.getMatchesRated() + 1);
            stats.markDirty(modeId, uuid);
        }
    }
}
