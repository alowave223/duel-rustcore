package net.rustcore.duel.kit;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.db.dao.KitLayoutsDao;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KitLayoutManager {

    private final DuelsPlugin plugin;
    private final KitLayoutsDao dao;
    private final Map<UUID, Map<String, KitLayout>> layouts = new ConcurrentHashMap<>();

    public KitLayoutManager(DuelsPlugin plugin, KitLayoutsDao dao) {
        this.plugin = plugin;
        this.dao = dao;
    }

    public static KitLayoutManager forTest(KitLayoutsDao dao) {
        return new KitLayoutManager(null, dao);
    }

    private Map<String, KitLayout> ensureLoaded(UUID uuid) {
        return layouts.computeIfAbsent(uuid, k -> {
            Map<String, KitLayout> out = new ConcurrentHashMap<>();
            for (Map.Entry<String, Map<Integer, Integer>> e : dao.load(k).entrySet()) {
                out.put(e.getKey(), new KitLayout(e.getValue()));
            }
            return out;
        });
    }

    public KitLayout getLayout(UUID uid, String modeId) {
        return ensureLoaded(uid).get(modeId);
    }

    public void setLayout(UUID uid, String modeId, KitLayout layout) {
        ensureLoaded(uid).put(modeId, layout);
        dao.upsertLayout(uid, modeId, new HashMap<>(layout.getRaw()));
    }

    public void resetLayout(UUID uid, String modeId) {
        ensureLoaded(uid).remove(modeId);
        dao.deleteLayout(uid, modeId);
    }

    public void load() { /* lazy */ }
    public void save() { /* synchronous writes on mutation */ }
}
