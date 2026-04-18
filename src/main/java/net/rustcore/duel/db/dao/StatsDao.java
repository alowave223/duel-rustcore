package net.rustcore.duel.db.dao;

import net.rustcore.duel.db.DaoSupport;
import net.rustcore.duel.stats.PlayerStats;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class StatsDao {

    private static final String SQL_UPSERT_MYSQL =
            "INSERT INTO duels_stats (mode_id, player_uuid, wins, losses, kills, deaths, win_streak, best_win_streak, elo)" +
            " VALUES (?,?,?,?,?,?,?,?,?)" +
            " ON DUPLICATE KEY UPDATE wins=VALUES(wins), losses=VALUES(losses), kills=VALUES(kills)," +
            " deaths=VALUES(deaths), win_streak=VALUES(win_streak), best_win_streak=VALUES(best_win_streak), elo=VALUES(elo)";

    private static final String SQL_UPSERT_H2 =
            "MERGE INTO duels_stats (mode_id, player_uuid, wins, losses, kills, deaths, win_streak, best_win_streak, elo)" +
            " KEY (mode_id, player_uuid) VALUES (?,?,?,?,?,?,?,?,?)";

    private static final String SQL_LOAD_ALL =
            "SELECT player_uuid, wins, losses, kills, deaths, win_streak, best_win_streak, elo FROM duels_stats WHERE mode_id=?";

    private final DaoSupport s;
    private final String upsertSql;

    public StatsDao(DataSource ds) {
        this.s = new DaoSupport(ds);
        this.upsertSql = s.isMySql() ? SQL_UPSERT_MYSQL : SQL_UPSERT_H2;
    }

    public void upsert(String modeId, UUID uuid, PlayerStats p) {
        s.execute(upsertSql, ps -> bind(ps, modeId, uuid, p));
    }

    public void upsertBatch(String modeId, Map<UUID, PlayerStats> map) {
        if (map.isEmpty()) return;
        s.withTx(c -> {
            try (PreparedStatement ps = c.prepareStatement(upsertSql)) {
                for (var e : map.entrySet()) {
                    bind(ps, modeId, e.getKey(), e.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return null;
        });
    }

    public Map<UUID, PlayerStats> loadAll(String modeId) {
        Map<UUID, PlayerStats> out = new HashMap<>();
        s.queryList(SQL_LOAD_ALL, ps -> ps.setString(1, modeId), rs -> {
            PlayerStats p = new PlayerStats();
            UUID u = UUID.fromString(rs.getString(1));
            p.setWins(rs.getInt(2));
            p.setLosses(rs.getInt(3));
            p.setKills(rs.getInt(4));
            p.setDeaths(rs.getInt(5));
            p.setWinStreak(rs.getInt(6));
            p.setBestWinStreak(rs.getInt(7));
            p.setElo(rs.getInt(8));
            out.put(u, p);
            return null;
        });
        return out;
    }

    private static void bind(PreparedStatement ps, String modeId, UUID u, PlayerStats p) throws java.sql.SQLException {
        ps.setString(1, modeId);
        ps.setString(2, u.toString());
        ps.setInt(3, p.getWins());
        ps.setInt(4, p.getLosses());
        ps.setInt(5, p.getKills());
        ps.setInt(6, p.getDeaths());
        ps.setInt(7, p.getWinStreak());
        ps.setInt(8, p.getBestWinStreak());
        ps.setInt(9, p.getElo());
    }
}
