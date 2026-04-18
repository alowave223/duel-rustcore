package net.rustcore.duel.db.dao;

import net.rustcore.duel.db.DaoSupport;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class KitLayoutsDao {

    private final DaoSupport s;

    public KitLayoutsDao(DataSource ds) { this.s = new DaoSupport(ds); }

    public Map<String, Map<Integer, Integer>> load(UUID u) {
        Map<String, Map<Integer, Integer>> out = new HashMap<>();
        s.queryList(
                "SELECT mode_id, src_slot, dst_slot FROM duels_kit_layouts WHERE player_uuid=?",
                ps -> ps.setString(1, u.toString()),
                rs -> {
                    out.computeIfAbsent(rs.getString(1), k -> new LinkedHashMap<>())
                            .put(rs.getInt(2), rs.getInt(3));
                    return null;
                });
        return out;
    }

    public void upsertLayout(UUID u, String modeId, Map<Integer, Integer> raw) {
        s.withTx(c -> {
            try (PreparedStatement del = c.prepareStatement(
                    "DELETE FROM duels_kit_layouts WHERE player_uuid=? AND mode_id=?")) {
                del.setString(1, u.toString()); del.setString(2, modeId);
                del.executeUpdate();
            }
            if (raw.isEmpty()) return null;
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO duels_kit_layouts (player_uuid, mode_id, src_slot, dst_slot) VALUES (?,?,?,?)")) {
                for (var e : raw.entrySet()) {
                    ins.setString(1, u.toString());
                    ins.setString(2, modeId);
                    ins.setInt(3, e.getKey());
                    ins.setInt(4, e.getValue());
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            return null;
        });
    }

    public void deleteLayout(UUID u, String modeId) {
        s.execute("DELETE FROM duels_kit_layouts WHERE player_uuid=? AND mode_id=?",
                ps -> { ps.setString(1, u.toString()); ps.setString(2, modeId); });
    }
}
