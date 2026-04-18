package net.rustcore.duel.settings;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.db.dao.SettingsDao;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SettingsManager {

    private final DuelsPlugin plugin;
    private final SettingsDao dao;
    private final Map<UUID, PlayerSettings> cache = new ConcurrentHashMap<>();

    public SettingsManager(DuelsPlugin plugin, SettingsDao dao) {
        this.plugin = plugin;
        this.dao = dao;
    }

    public static SettingsManager forTest(SettingsDao dao) { return new SettingsManager(null, dao); }

    public PlayerSettings getSettings(UUID u) {
        return cache.computeIfAbsent(u, k -> dao.load(k).orElseGet(PlayerSettings::new));
    }

    public void update(UUID u, PlayerSettings s) {
        cache.put(u, s);
        dao.upsert(u, s);
    }

    public void load() { /* lazy */ }
    public void save() { /* writes synchronous */ }
}
