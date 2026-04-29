package net.rustcore.duel.stats;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.db.WriteBehindQueue;
import net.rustcore.duel.db.dao.StatsDao;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StatsManager {

    private record ModeKey(String modeId, UUID uuid) {}

    private final DuelsPlugin plugin;
    private final StatsDao dao;
    private final Map<String, Map<UUID, PlayerStats>> statsCache = new ConcurrentHashMap<>();
    private final WriteBehindQueue<ModeKey> dirty;

    public StatsManager(DuelsPlugin plugin, StatsDao dao) {
        this.plugin = plugin;
        this.dao = dao;
        this.dirty = new WriteBehindQueue<>(this::flushBatch);
    }

    public static StatsManager forTest(StatsDao dao) {
        return new StatsManager(null, dao);
    }

    public void registerMode(String modeId) {
        statsCache.put(modeId, new ConcurrentHashMap<>(dao.loadAll(modeId)));
    }

    public PlayerStats getStats(String modeId, UUID playerId) {
        Map<UUID, PlayerStats> modeStats = statsCache.computeIfAbsent(modeId, k -> new ConcurrentHashMap<>());
        return modeStats.computeIfAbsent(playerId, k -> new PlayerStats());
    }

    public void recordResult(String modeId, UUID winnerId, UUID loserId) {
        PlayerStats winnerStats = getStats(modeId, winnerId);
        PlayerStats loserStats = loserId != null ? getStats(modeId, loserId) : null;

        synchronized (winnerStats) {
            winnerStats.setWins(winnerStats.getWins() + 1);
            winnerStats.setWinStreak(winnerStats.getWinStreak() + 1);
            if (winnerStats.getWinStreak() > winnerStats.getBestWinStreak()) {
                winnerStats.setBestWinStreak(winnerStats.getWinStreak());
            }
            if (loserStats != null) {
                synchronized (loserStats) {
                    loserStats.setLosses(loserStats.getLosses() + 1);
                    loserStats.setWinStreak(0);
                    dirty.markDirty(new ModeKey(modeId, loserId));
                }
            }
            dirty.markDirty(new ModeKey(modeId, winnerId));
        }
        scheduleFlush();
    }

    public void recordKill(String modeId, UUID killerId) {
        PlayerStats stats = getStats(modeId, killerId);
        stats.setKills(stats.getKills() + 1);
        dirty.markDirty(new ModeKey(modeId, killerId));
    }

    public void recordDeath(String modeId, UUID playerId) {
        PlayerStats stats = getStats(modeId, playerId);
        stats.setDeaths(stats.getDeaths() + 1);
        dirty.markDirty(new ModeKey(modeId, playerId));
    }

    public List<Map.Entry<UUID, PlayerStats>> getLeaderboard(String modeId, String stat, int limit) {
        Map<UUID, PlayerStats> modeStats = statsCache.getOrDefault(modeId, Map.of());
        List<Map.Entry<UUID, PlayerStats>> entries = new ArrayList<>(modeStats.entrySet());
        Comparator<Map.Entry<UUID, PlayerStats>> comparator = switch (stat.toLowerCase()) {
            case "wins" -> Comparator.comparingInt(e -> -e.getValue().getWins());
            case "rating_ordinal", "elo" -> Comparator.comparingDouble(e -> -e.getValue().getRatingOrdinal());
            case "kills" -> Comparator.comparingInt(e -> -e.getValue().getKills());
            case "win_streak", "best_win_streak" -> Comparator.comparingInt(e -> -e.getValue().getBestWinStreak());
            default -> Comparator.comparingDouble(e -> -e.getValue().getRatingOrdinal());
        };
        entries.sort(comparator);
        return entries.subList(0, Math.min(limit, entries.size()));
    }

    private void scheduleFlush() {
        if (plugin == null) return;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, dirty::flushNow);
    }

    public boolean hasStats(String modeId, UUID uuid) {
        Map<UUID, PlayerStats> modeStats = statsCache.get(modeId);
        return modeStats != null && modeStats.containsKey(uuid);
    }

    /**
     * Single-lookup variant that eliminates the hasStats/getStats race window.
     * Returns an empty Optional if the player's stats are not in cache.
     */
    public Optional<PlayerStats> findStats(String modeId, UUID uuid) {
        Map<UUID, PlayerStats> modeCache = statsCache.get(modeId);
        if (modeCache == null) return Optional.empty();
        return Optional.ofNullable(modeCache.get(uuid));
    }

    public void markDirty(String modeId, UUID uuid) {
        dirty.markDirty(new ModeKey(modeId, uuid));
    }

    public void flushAll() { dirty.flushNow(); }

    public void saveAll() { flushAll(); }

    private void flushBatch(Set<ModeKey> batch) {
        Map<String, Map<UUID, PlayerStats>> byMode = new HashMap<>();
        for (ModeKey k : batch) {
            PlayerStats p = statsCache.getOrDefault(k.modeId(), Map.of()).get(k.uuid());
            if (p == null) continue;
            byMode.computeIfAbsent(k.modeId(), x -> new HashMap<>()).put(k.uuid(), p.snapshot());
        }
        byMode.forEach(dao::upsertBatch);
    }
}
