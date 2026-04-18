package net.rustcore.duel.db.dao;

import net.rustcore.duel.db.DaoSupport;

import javax.sql.DataSource;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class FriendsDao {

    private final DaoSupport s;

    public FriendsDao(DataSource ds) { this.s = new DaoSupport(ds); }

    public Set<UUID> loadFriends(UUID uuid) {
        Set<UUID> out = new HashSet<>();
        s.queryList(
                "SELECT CASE WHEN player_a=? THEN player_b ELSE player_a END AS friend " +
                "FROM duels_friends WHERE player_a=? OR player_b=?",
                ps -> { ps.setString(1, uuid.toString()); ps.setString(2, uuid.toString()); ps.setString(3, uuid.toString()); },
                rs -> { out.add(UUID.fromString(rs.getString(1))); return null; });
        return out;
    }

    public void addFriendPair(UUID a, UUID b) {
        UUID[] o = canonical(a, b);
        String sql = s.isMySql()
                ? "INSERT IGNORE INTO duels_friends (player_a, player_b) VALUES (?, ?)"
                : "MERGE INTO duels_friends (player_a, player_b) KEY (player_a, player_b) VALUES (?, ?)";
        s.execute(sql, ps -> { ps.setString(1, o[0].toString()); ps.setString(2, o[1].toString()); });
    }

    public void removeFriendPair(UUID a, UUID b) {
        UUID[] o = canonical(a, b);
        s.execute("DELETE FROM duels_friends WHERE player_a=? AND player_b=?",
                ps -> { ps.setString(1, o[0].toString()); ps.setString(2, o[1].toString()); });
    }

    private static UUID[] canonical(UUID a, UUID b) {
        return a.toString().compareTo(b.toString()) <= 0 ? new UUID[]{a, b} : new UUID[]{b, a};
    }
}
