package net.rustcore.duel.duel;

import net.rustcore.duel.db.dao.RankedPrefsDao;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RankedPreferenceStore {

    private final RankedPrefsDao dao;
    private final Map<UUID, Boolean> cache = new ConcurrentHashMap<>();

    public RankedPreferenceStore(RankedPrefsDao dao) {
        this.dao = dao;
        this.cache.putAll(dao.loadAll());
    }

    public boolean isRanked(UUID u) {
        return cache.getOrDefault(u, false);
    }

    public void setRanked(UUID u, boolean ranked) {
        cache.put(u, ranked);
        dao.upsert(u, ranked);
    }
}
