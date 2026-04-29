package net.rustcore.duel.db;

import net.rustcore.duel.db.dao.FriendsDao;
import net.rustcore.duel.db.dao.KitLayoutsDao;
import net.rustcore.duel.db.dao.RankedPrefsDao;
import net.rustcore.duel.db.dao.SettingsDao;
import net.rustcore.duel.db.dao.StatsDao;
import net.rustcore.duel.settings.PlayerSettings;
import net.rustcore.duel.stats.PlayerStats;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MigrationService {

    private final File dataFolder;
    private final StatsDao stats;
    private final FriendsDao friends;
    private final SettingsDao settings;
    private final KitLayoutsDao layouts;
    private final RankedPrefsDao ranked;

    public MigrationService(File dataFolder, StatsDao s, FriendsDao f, SettingsDao st,
                            KitLayoutsDao l, RankedPrefsDao r) {
        this.dataFolder = dataFolder;
        this.stats = s;
        this.friends = f;
        this.settings = st;
        this.layouts = l;
        this.ranked = r;
    }

    public void runIfNeeded(List<String> modeIds) {
        for (String mode : modeIds) importStats(mode);
        importFriends();
        importSettings();
        importKitLayouts();
        importRankedPrefs();
    }

    private void importStats(String modeId) {
        File f = new File(dataFolder, "stats/" + modeId + "_stats.yml");
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection players = y.getConfigurationSection("players");
        if (players == null) { markDone(f); return; }
        Map<UUID, PlayerStats> batch = new HashMap<>();
        for (String key : players.getKeys(false)) {
            try {
                UUID u = UUID.fromString(key);
                ConfigurationSection p = players.getConfigurationSection(key);
                if (p == null) continue;
                PlayerStats ps = new PlayerStats();
                ps.setWins(p.getInt("wins", 0));
                ps.setLosses(p.getInt("losses", 0));
                ps.setKills(p.getInt("kills", 0));
                ps.setDeaths(p.getInt("deaths", 0));
                ps.setWinStreak(p.getInt("win_streak", 0));
                ps.setBestWinStreak(p.getInt("best_win_streak", 0));
                ps.setRating(PlayerStats.DEFAULT_MU, PlayerStats.DEFAULT_SIGMA, 0.0);
                batch.put(u, ps);
            } catch (IllegalArgumentException ignored) {}
        }
        if (!batch.isEmpty()) stats.upsertBatch(modeId, batch);
        markDone(f);
    }

    private void importFriends() {
        File f = new File(dataFolder, "friends.yml");
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection players = y.getConfigurationSection("players");
        if (players == null) { markDone(f); return; }
        for (String key : players.getKeys(false)) {
            try {
                UUID u = UUID.fromString(key);
                for (String friend : players.getStringList(key + ".friends")) {
                    try { friends.addFriendPair(u, UUID.fromString(friend)); }
                    catch (IllegalArgumentException ignored) {}
                }
            } catch (IllegalArgumentException ignored) {}
        }
        markDone(f);
    }

    private void importSettings() {
        File f = new File(dataFolder, "player_settings.yml");
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection players = y.getConfigurationSection("players");
        if (players == null) { markDone(f); return; }
        for (String key : players.getKeys(false)) {
            try {
                UUID u = UUID.fromString(key);
                PlayerSettings ps = new PlayerSettings();
                ps.setWhoCanInviteToParty(PlayerSettings.Visibility.valueOf(
                        players.getString(key + ".party-invites", "ALL")));
                ps.setWhoCanChallenge(PlayerSettings.Visibility.valueOf(
                        players.getString(key + ".challenges", "ALL")));
                ps.setAcceptFriendRequests(players.getBoolean(key + ".friend-requests", true));
                ps.setStatus(PlayerSettings.Status.valueOf(
                        players.getString(key + ".status", "ONLINE")));
                settings.upsert(u, ps);
            } catch (IllegalArgumentException ignored) {}
        }
        markDone(f);
    }

    private void importKitLayouts() {
        File f = new File(dataFolder, "kit_layouts.yml");
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection players = y.getConfigurationSection("players");
        if (players == null) { markDone(f); return; }
        for (String uidKey : players.getKeys(false)) {
            try {
                UUID u = UUID.fromString(uidKey);
                ConfigurationSection modes = players.getConfigurationSection(uidKey);
                if (modes == null) continue;
                for (String modeId : modes.getKeys(false)) {
                    ConfigurationSection m = modes.getConfigurationSection(modeId);
                    if (m == null) continue;
                    Map<Integer, Integer> raw = new LinkedHashMap<>();
                    for (String src : m.getKeys(false)) {
                        try { raw.put(Integer.parseInt(src), m.getInt(src)); }
                        catch (NumberFormatException ignored) {}
                    }
                    layouts.upsertLayout(u, modeId, raw);
                }
            } catch (IllegalArgumentException ignored) {}
        }
        markDone(f);
    }

    private void importRankedPrefs() {
        File f = new File(dataFolder, "ranked_preferences.yml");
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection players = y.getConfigurationSection("players");
        if (players == null) { markDone(f); return; }
        for (String key : players.getKeys(false)) {
            try {
                UUID u = UUID.fromString(key);
                ranked.upsert(u, players.getBoolean(key));
            } catch (IllegalArgumentException ignored) {}
        }
        markDone(f);
    }

    private void markDone(File f) {
        File target = new File(f.getParentFile(), f.getName() + ".migrated");
        if (target.exists()) target.delete();
        f.renameTo(target);
    }
}
