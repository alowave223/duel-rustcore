package net.rustcore.duel.stats;

import net.rustcore.duel.DuelsPlugin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages per-mode player statistics.
 * Each mode has its own stats file (configured in the mode's yml).
 * Stats are stored in YAML format for simplicity; can be swapped to a DB later.
 */
public class StatsManager {

    private final DuelsPlugin plugin;
    // modeId -> (playerUUID -> PlayerStats)
    private final Map<String, Map<UUID, PlayerStats>> statsCache = new ConcurrentHashMap<>();
    // modeId -> YamlConfiguration
    private final Map<String, YamlConfiguration> configFiles = new ConcurrentHashMap<>();
    // modeId -> File
    private final Map<String, File> fileMap = new ConcurrentHashMap<>();

    private record ModeEloConfig(int startingElo, int eloKFactor) {}

    private final Map<String, ModeEloConfig> eloConfigs = new ConcurrentHashMap<>();

    public StatsManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Register a mode's stats file.
     */
    public void registerMode(String modeId, String statsFilePath, int startingElo, int eloKFactor) {
        eloConfigs.put(modeId, new ModeEloConfig(startingElo, eloKFactor));

        File file = new File(plugin.getDataFolder(), statsFilePath);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        YamlConfiguration config;
        if (file.exists()) {
            config = YamlConfiguration.loadConfiguration(file);
        } else {
            config = new YamlConfiguration();
        }

        fileMap.put(modeId, file);
        configFiles.put(modeId, config);

        // Load existing stats into cache
        Map<UUID, PlayerStats> modeStats = new ConcurrentHashMap<>();
        ConfigurationSection playersSection = config.getConfigurationSection("players");
        if (playersSection != null) {
            for (String uuidStr : playersSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    ConfigurationSection pSection = playersSection.getConfigurationSection(uuidStr);
                    if (pSection != null) {
                        PlayerStats stats = new PlayerStats();
                        stats.setWins(pSection.getInt("wins", 0));
                        stats.setLosses(pSection.getInt("losses", 0));
                        stats.setKills(pSection.getInt("kills", 0));
                        stats.setDeaths(pSection.getInt("deaths", 0));
                        stats.setWinStreak(pSection.getInt("win_streak", 0));
                        stats.setBestWinStreak(pSection.getInt("best_win_streak", 0));
                        stats.setElo(pSection.getInt("elo", startingElo));
                        modeStats.put(uuid, stats);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }

        statsCache.put(modeId, modeStats);
        plugin.getLogger().info("Loaded " + modeStats.size() + " player stats for mode: " + modeId);
    }

    /**
     * Get stats for a player in a mode.
     */
    public PlayerStats getStats(String modeId, UUID playerId) {
        ModeEloConfig eloConfig = eloConfigs.getOrDefault(modeId, new ModeEloConfig(1000, 32));
        Map<UUID, PlayerStats> modeStats = statsCache.computeIfAbsent(modeId, k -> new ConcurrentHashMap<>());
        return modeStats.computeIfAbsent(playerId, k -> {
            PlayerStats stats = new PlayerStats();
            stats.setElo(eloConfig.startingElo());
            return stats;
        });
    }

    /**
     * Record a duel result (winner vs loser).
     * All stat mutations are done inside a single synchronized block per object
     * to prevent race conditions with async saves.
     */
    public void recordResult(String modeId, UUID winnerId, UUID loserId) {
        PlayerStats winnerStats = getStats(modeId, winnerId);
        PlayerStats loserStats = loserId != null ? getStats(modeId, loserId) : null;

        ModeEloConfig eloConfig = eloConfigs.getOrDefault(modeId, new ModeEloConfig(1000, 32));

        // Lock winner first, then loser (consistent ordering prevents deadlock)
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

                    // ELO calculation uses values captured inside the same lock
                    double expectedWin = 1.0 / (1.0 + Math.pow(10, (loserStats.getElo() - winnerStats.getElo()) / 400.0));
                    int winnerEloChange = (int) Math.round(eloConfig.eloKFactor() * (1.0 - expectedWin));
                    int loserEloChange = (int) Math.round(eloConfig.eloKFactor() * (0.0 - (1.0 - expectedWin)));

                    winnerStats.setElo(Math.max(0, winnerStats.getElo() + winnerEloChange));
                    loserStats.setElo(Math.max(0, loserStats.getElo() + loserEloChange));
                }
            }
        }

        // Save async
        saveAsync(modeId);
    }

    /**
     * Record a kill for a player.
     */
    public void recordKill(String modeId, UUID killerId) {
        PlayerStats stats = getStats(modeId, killerId);
        stats.setKills(stats.getKills() + 1);
    }

    /**
     * Record a death for a player.
     */
    public void recordDeath(String modeId, UUID playerId) {
        PlayerStats stats = getStats(modeId, playerId);
        stats.setDeaths(stats.getDeaths() + 1);
    }

    /**
     * Get leaderboard sorted by a stat.
     */
    public List<Map.Entry<UUID, PlayerStats>> getLeaderboard(String modeId, String stat, int limit) {
        Map<UUID, PlayerStats> modeStats = statsCache.getOrDefault(modeId, Map.of());
        List<Map.Entry<UUID, PlayerStats>> entries = new ArrayList<>(modeStats.entrySet());

        Comparator<Map.Entry<UUID, PlayerStats>> comparator = switch (stat.toLowerCase()) {
            case "wins" -> Comparator.comparingInt(e -> -e.getValue().getWins());
            case "elo" -> Comparator.comparingInt(e -> -e.getValue().getElo());
            case "kills" -> Comparator.comparingInt(e -> -e.getValue().getKills());
            case "win_streak", "best_win_streak" -> Comparator.comparingInt(e -> -e.getValue().getBestWinStreak());
            default -> Comparator.comparingInt(e -> -e.getValue().getElo());
        };

        entries.sort(comparator);
        return entries.subList(0, Math.min(limit, entries.size()));
    }

    private void saveAsync(String modeId) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> save(modeId));
    }

    private synchronized void save(String modeId) {
        YamlConfiguration config = configFiles.get(modeId);
        File file = fileMap.get(modeId);
        if (config == null || file == null) return;

        Map<UUID, PlayerStats> modeStats = statsCache.get(modeId);
        if (modeStats == null) return;

        for (Map.Entry<UUID, PlayerStats> entry : modeStats.entrySet()) {
            String path = "players." + entry.getKey().toString();
            PlayerStats stats = entry.getValue().snapshot();
            config.set(path + ".wins", stats.getWins());
            config.set(path + ".losses", stats.getLosses());
            config.set(path + ".kills", stats.getKills());
            config.set(path + ".deaths", stats.getDeaths());
            config.set(path + ".win_streak", stats.getWinStreak());
            config.set(path + ".best_win_streak", stats.getBestWinStreak());
            config.set(path + ".elo", stats.getElo());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save stats for mode: " + modeId, e);
        }
    }

    /**
     * Save all stats to disk.
     */
    public void saveAll() {
        for (String modeId : configFiles.keySet()) {
            save(modeId);
        }
    }
}
