package net.rustcore.duel.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.duel.Duel;
import net.rustcore.duel.duel.DuelQueue;
import net.rustcore.duel.duel.DuelState;
import net.rustcore.duel.stats.PlayerStats;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlaceholderAPI expansion for RustCore-Duels.
 *
 * <h3>Stats placeholders — per mode:</h3>
 * 
 * <pre>
 *   %duels_stats_<mode>_elo%
 *   %duels_stats_<mode>_wins%
 *   %duels_stats_<mode>_losses%
 *   %duels_stats_<mode>_kills%
 *   %duels_stats_<mode>_deaths%
 *   %duels_stats_<mode>_kdr%
 *   %duels_stats_<mode>_winrate%
 *   %duels_stats_<mode>_winstreak%
 *   %duels_stats_<mode>_bestwinstreak%
 * </pre>
 *
 * <h3>Rank placeholders — position on leaderboard:</h3>
 * 
 * <pre>
 *   %duels_rank_<mode>_elo%
 *   %duels_rank_<mode>_wins%
 *   %duels_rank_<mode>_kills%
 *   %duels_rank_<mode>_winstreak%
 * </pre>
 *
 * <h3>Active duel placeholders:</h3>
 * 
 * <pre>
 *   %duels_duel_active%           → true / false
 *   %duels_duel_state%            → PREPARING / DRAFTING / COUNTDOWN / ACTIVE / ROUND_ENDING / ENDED
 *   %duels_duel_mode%             → mode display name
 *   %duels_duel_round%            → current round number
 *   %duels_duel_bestof%           → best-of series length
 *   %duels_duel_score%            → "1-0" (my score - opponent score)
 *   %duels_duel_my_score%         → rounds won by this player
 *   %duels_duel_opponent_score%   → rounds won by opponent
 *   %duels_duel_opponent%         → opponent display name
 *   %duels_duel_arena%            → arena display name
 * </pre>
 *
 * <h3>Queue placeholders:</h3>
 * 
 * <pre>
 *   %duels_queue_active%              → true / false (is player in queue)
 *   %duels_queue_mode%                → mode player is queued for
 *   %duels_queue_size%                → total players across all queues
 *   %duels_queue_size_<mode>%         → players waiting in a specific mode queue
 * </pre>
 *
 * <h3>Leaderboard placeholders (top-10, cached 5 s):</h3>
 * 
 * <pre>
 *   %duels_top_<mode>_<stat>_<pos>_name%    → player name at rank <pos>
 *   %duels_top_<mode>_<stat>_<pos>_value%   → stat value at rank <pos>
 *   Stats: elo, wins, kills, winstreak
 *   Positions: 1–10
 * </pre>
 */
public class DuelsExpansion extends PlaceholderExpansion {

    private static final String EMPTY = "";

    private final DuelsPlugin plugin;

    // Leaderboard cache: "mode:stat" → snapshot list (top 10)
    private final Map<String, List<LeaderboardEntry>> leaderboardCache = new ConcurrentHashMap<>();
    // When the cache entry was last refreshed (ms)
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5_000;

    public DuelsExpansion(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // PlaceholderExpansion contract
    // -------------------------------------------------------------------------

    @Override
    public @NotNull String getIdentifier() {
        return "duels";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    /** Keep the expansion registered even without a PAPI ecloud listing. */
    @Override
    public boolean persist() {
        return true;
    }

    // -------------------------------------------------------------------------
    // Main resolver
    // -------------------------------------------------------------------------

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null)
            return EMPTY;

        // Split on _ but limit to 2 parts: category + rest
        int firstUnderscore = params.indexOf('_');
        if (firstUnderscore < 0)
            return EMPTY;

        String category = params.substring(0, firstUnderscore).toLowerCase();
        String rest = params.substring(firstUnderscore + 1);

        return switch (category) {
            case "stats" -> resolveStats(offlinePlayer, rest);
            case "rank" -> resolveRank(offlinePlayer, rest);
            case "duel" -> resolveDuel(offlinePlayer, rest);
            case "queue" -> resolveQueue(offlinePlayer, rest);
            case "top" -> resolveTop(rest);
            default -> EMPTY;
        };
    }

    // -------------------------------------------------------------------------
    // stats_<mode>_<stat>
    // -------------------------------------------------------------------------

    private String resolveStats(OfflinePlayer player, String rest) {
        // rest = "<mode>_<stat>"
        int sep = rest.lastIndexOf('_');
        if (sep < 0)
            return EMPTY;

        String modeId = rest.substring(0, sep).toLowerCase();
        String stat = rest.substring(sep + 1).toLowerCase();

        PlayerStats stats = plugin.getStatsManager().getStats(modeId, player.getUniqueId());

        return switch (stat) {
            case "elo" -> String.valueOf(stats.getElo());
            case "wins" -> String.valueOf(stats.getWins());
            case "losses" -> String.valueOf(stats.getLosses());
            case "kills" -> String.valueOf(stats.getKills());
            case "deaths" -> String.valueOf(stats.getDeaths());
            case "kdr" -> String.format("%.2f", stats.getKdr());
            case "winrate" -> String.format("%.1f", stats.getWinRate());
            case "winstreak" -> String.valueOf(stats.getWinStreak());
            case "bestwinstreak" -> String.valueOf(stats.getBestWinStreak());
            default -> EMPTY;
        };
    }

    // -------------------------------------------------------------------------
    // rank_<mode>_<stat>
    // -------------------------------------------------------------------------

    private String resolveRank(OfflinePlayer player, String rest) {
        // rest = "<mode>_<stat>"
        int sep = rest.lastIndexOf('_');
        if (sep < 0)
            return EMPTY;

        String modeId = rest.substring(0, sep).toLowerCase();
        String stat = rest.substring(sep + 1).toLowerCase();

        List<LeaderboardEntry> board = getCachedLeaderboard(modeId, stat, Integer.MAX_VALUE);
        UUID target = player.getUniqueId();

        for (int i = 0; i < board.size(); i++) {
            if (board.get(i).uuid().equals(target)) {
                return String.valueOf(i + 1);
            }
        }
        // Player not on board yet — return total size + 1 as "unranked" position
        return String.valueOf(board.size() + 1);
    }

    // -------------------------------------------------------------------------
    // duel_<field>
    // -------------------------------------------------------------------------

    private String resolveDuel(OfflinePlayer offlinePlayer, String field) {
        UUID uuid = offlinePlayer.getUniqueId();
        Duel duel = plugin.getDuelManager().getDuel(uuid);

        if (field.equalsIgnoreCase("active")) {
            return duel != null ? "true" : "false";
        }

        if (duel == null)
            return EMPTY;

        return switch (field.toLowerCase()) {
            case "state" -> formatState(duel.getState());
            case "mode" -> duel.getMode().getDisplayName();
            case "round" -> String.valueOf(duel.getCurrentRound());
            case "bestof" -> String.valueOf(duel.getBestOf());
            case "my_score" -> String.valueOf(duel.getScores().getOrDefault(uuid, 0));
            case "opponent_score" -> String.valueOf(opponentScore(duel, uuid));
            case "score" -> duel.getScores().getOrDefault(uuid, 0)
                    + "-" + opponentScore(duel, uuid);
            case "opponent" -> opponentName(duel, uuid);
            case "arena" -> duel.getActiveArena().getTemplate().getDisplayName();
            default -> EMPTY;
        };
    }

    private int opponentScore(Duel duel, UUID self) {
        for (Map.Entry<UUID, Integer> entry : duel.getScores().entrySet()) {
            if (!entry.getKey().equals(self))
                return entry.getValue();
        }
        return 0;
    }

    private String opponentName(Duel duel, UUID self) {
        for (UUID pid : duel.getPlayerIds()) {
            if (!pid.equals(self)) {
                Player p = Bukkit.getPlayer(pid);
                if (p != null)
                    return p.getName();
                OfflinePlayer op = Bukkit.getOfflinePlayer(pid);
                return op.getName() != null ? op.getName() : "???";
            }
        }
        return EMPTY;
    }

    private String formatState(DuelState state) {
        return switch (state) {
            case PREPARING -> "Preparing";
            case DRAFTING -> "Drafting";
            case COUNTDOWN -> "Countdown";
            case ACTIVE -> "Active";
            case ROUND_ENDING -> "Round Ending";
            case ENDED -> "Ended";
        };
    }

    // -------------------------------------------------------------------------
    // queue_<field> / queue_size_<mode>
    // -------------------------------------------------------------------------

    private String resolveQueue(OfflinePlayer player, String rest) {
        DuelQueue queue = plugin.getDuelManager().getQueue();
        UUID uuid = player.getUniqueId();

        if (rest.equalsIgnoreCase("active")) {
            return queue.isQueued(uuid) ? "true" : "false";
        }

        if (rest.equalsIgnoreCase("mode")) {
            String mode = queue.getQueuedMode(uuid);
            return mode != null ? mode : EMPTY;
        }

        if (rest.equalsIgnoreCase("size")) {
            // Sum across all mode queues
            int total = plugin.getModeManager().getAllModes().stream()
                    .mapToInt(m -> queue.getQueueSize(m.getId()))
                    .sum();
            return String.valueOf(total);
        }

        // queue_size_<mode>
        if (rest.toLowerCase().startsWith("size_")) {
            String modeId = rest.substring(5).toLowerCase();
            return String.valueOf(queue.getQueueSize(modeId));
        }

        return EMPTY;
    }

    // -------------------------------------------------------------------------
    // top_<mode>_<stat>_<pos>_<field>
    // -------------------------------------------------------------------------

    private String resolveTop(String rest) {
        // parts: [mode, stat, pos, field] — mode itself may contain underscores,
        // but stat is always the known word before the numeric position.
        // Strategy: find the last three underscores to extract pos and field,
        // then split mode/stat by the last underscore in the remaining prefix.
        String[] parts = rest.split("_");
        if (parts.length < 4)
            return EMPTY;

        // field = last token
        // pos = second-to-last token (must be numeric)
        // stat = third-to-last token
        // mode = everything before that
        String fieldToken = parts[parts.length - 1].toLowerCase();
        String posToken = parts[parts.length - 2];
        String statToken = parts[parts.length - 3].toLowerCase();
        String modeToken = String.join("_", Arrays.copyOf(parts, parts.length - 3)).toLowerCase();

        int pos;
        try {
            pos = Integer.parseInt(posToken);
        } catch (NumberFormatException e) {
            return EMPTY;
        }
        if (pos < 1 || pos > 10)
            return EMPTY;

        List<LeaderboardEntry> board = getCachedLeaderboard(modeToken, statToken, 10);
        if (pos > board.size())
            return EMPTY;

        LeaderboardEntry entry = board.get(pos - 1);

        return switch (fieldToken) {
            case "name" -> entry.name();
            case "value" -> String.valueOf(entry.value());
            default -> EMPTY;
        };
    }

    // -------------------------------------------------------------------------
    // Leaderboard cache helpers
    // -------------------------------------------------------------------------

    private List<LeaderboardEntry> getCachedLeaderboard(String modeId, String stat, int limit) {
        String cacheKey = modeId + ":" + stat;
        long now = System.currentTimeMillis();
        Long lastRefresh = cacheTimestamps.get(cacheKey);

        if (lastRefresh == null || (now - lastRefresh) > CACHE_TTL_MS) {
            // Refresh synchronously (called from main thread by PAPI)
            List<Map.Entry<UUID, PlayerStats>> raw = plugin.getStatsManager().getLeaderboard(modeId, stat,
                    Math.min(limit, 10));

            List<LeaderboardEntry> snapshot = new ArrayList<>(raw.size());
            for (Map.Entry<UUID, PlayerStats> e : raw) {
                String name = resolvePlayerName(e.getKey());
                int value = statValue(e.getValue(), stat);
                snapshot.add(new LeaderboardEntry(e.getKey(), name, value));
            }

            leaderboardCache.put(cacheKey, snapshot);
            cacheTimestamps.put(cacheKey, now);
            return snapshot;
        }

        return leaderboardCache.getOrDefault(cacheKey, List.of());
    }

    private String resolvePlayerName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null)
            return online.getName();
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
    }

    private int statValue(PlayerStats stats, String stat) {
        return switch (stat.toLowerCase()) {
            case "elo" -> stats.getElo();
            case "wins" -> stats.getWins();
            case "kills" -> stats.getKills();
            case "winstreak" -> stats.getBestWinStreak();
            default -> stats.getElo();
        };
    }

    // -------------------------------------------------------------------------
    // Internal record
    // -------------------------------------------------------------------------

    private record LeaderboardEntry(UUID uuid, String name, int value) {
    }
}
