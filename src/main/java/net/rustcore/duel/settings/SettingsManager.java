package net.rustcore.duel.settings;

import net.rustcore.duel.DuelsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SettingsManager {

    private final DuelsPlugin plugin;
    private final Map<UUID, PlayerSettings> settings = new ConcurrentHashMap<>();
    private final File file;

    public SettingsManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/player_settings.yml");
    }

    public void load() {
        settings.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yml.getConfigurationSection("players");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            UUID uid;
            try { uid = UUID.fromString(key); } catch (IllegalArgumentException e) { continue; }
            PlayerSettings ps = new PlayerSettings();
            ps.setWhoCanInviteToParty(parseVis(section.getString(key + ".party-invites", "ALL")));
            ps.setWhoCanChallenge(parseVis(section.getString(key + ".challenges", "ALL")));
            ps.setAcceptFriendRequests(section.getBoolean(key + ".friend-requests", true));
            ps.setStatus(parseStatus(section.getString(key + ".status", "ONLINE")));
            settings.put(uid, ps);
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerSettings> e : settings.entrySet()) {
            String k = "players." + e.getKey();
            PlayerSettings ps = e.getValue();
            yml.set(k + ".party-invites", ps.getWhoCanInviteToParty().name());
            yml.set(k + ".challenges", ps.getWhoCanChallenge().name());
            yml.set(k + ".friend-requests", ps.isAcceptFriendRequests());
            yml.set(k + ".status", ps.getStatus().name());
        }
        try {
            file.getParentFile().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save player_settings.yml: " + ex.getMessage());
        }
    }

    public PlayerSettings getSettings(UUID uid) {
        return settings.computeIfAbsent(uid, k -> new PlayerSettings());
    }

    private PlayerSettings.Visibility parseVis(String s) {
        try { return PlayerSettings.Visibility.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return PlayerSettings.Visibility.ALL; }
    }

    private PlayerSettings.Status parseStatus(String s) {
        try { return PlayerSettings.Status.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return PlayerSettings.Status.ONLINE; }
    }
}
