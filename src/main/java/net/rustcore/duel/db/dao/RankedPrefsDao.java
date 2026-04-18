package net.rustcore.duel.db.dao;

import net.rustcore.duel.db.DaoSupport;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RankedPrefsDao {

    private static final String UPSERT_MYSQL =
            "INSERT INTO duels_ranked_prefs (player_uuid, ranked) VALUES (?, ?) " +
            "ON DUPLICATE KEY UPDATE ranked=VALUES(ranked)";
    private static final String UPSERT_H2 =
            "MERGE INTO duels_ranked_prefs (player_uuid, ranked) KEY (player_uuid) VALUES (?, ?)";

    private final DaoSupport s;
    private final String upsert;

    public RankedPrefsDao(DataSource ds) {
        this.s = new DaoSupport(ds);
        this.upsert = s.isMySql() ? UPSERT_MYSQL : UPSERT_H2;
    }

    public Map<UUID, Boolean> loadAll() {
        Map<UUID, Boolean> out = new HashMap<>();
        s.queryList("SELECT player_uuid, ranked FROM duels_ranked_prefs", null,
                rs -> { out.put(UUID.fromString(rs.getString(1)), rs.getBoolean(2)); return null; });
        return out;
    }

    public void upsert(UUID u, boolean ranked) {
        s.execute(upsert, ps -> { ps.setString(1, u.toString()); ps.setBoolean(2, ranked); });
    }
}
