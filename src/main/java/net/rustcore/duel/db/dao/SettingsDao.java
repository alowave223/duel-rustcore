package net.rustcore.duel.db.dao;

import net.rustcore.duel.db.DaoSupport;
import net.rustcore.duel.settings.PlayerSettings;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

public final class SettingsDao {

    private static final String UPSERT_MYSQL =
            "INSERT INTO duels_settings (player_uuid, party_invites, challenges, accept_friend_requests, status) " +
            "VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE " +
            "party_invites=VALUES(party_invites), challenges=VALUES(challenges), " +
            "accept_friend_requests=VALUES(accept_friend_requests), status=VALUES(status)";

    private static final String UPSERT_H2 =
            "MERGE INTO duels_settings (player_uuid, party_invites, challenges, accept_friend_requests, status) " +
            "KEY (player_uuid) VALUES (?,?,?,?,?)";

    private final DaoSupport s;
    private final String upsert;

    public SettingsDao(DataSource ds) {
        this.s = new DaoSupport(ds);
        this.upsert = s.isMySql() ? UPSERT_MYSQL : UPSERT_H2;
    }

    public Optional<PlayerSettings> load(UUID u) {
        return s.queryList(
                "SELECT party_invites, challenges, accept_friend_requests, status FROM duels_settings WHERE player_uuid=?",
                ps -> ps.setString(1, u.toString()),
                rs -> {
                    PlayerSettings p = new PlayerSettings();
                    p.setWhoCanInviteToParty(PlayerSettings.Visibility.valueOf(rs.getString(1)));
                    p.setWhoCanChallenge(PlayerSettings.Visibility.valueOf(rs.getString(2)));
                    p.setAcceptFriendRequests(rs.getBoolean(3));
                    p.setStatus(PlayerSettings.Status.valueOf(rs.getString(4)));
                    return p;
                }
        ).stream().findFirst();
    }

    public void upsert(UUID u, PlayerSettings p) {
        s.execute(upsert, ps -> {
            ps.setString(1, u.toString());
            ps.setString(2, p.getWhoCanInviteToParty().name());
            ps.setString(3, p.getWhoCanChallenge().name());
            ps.setBoolean(4, p.isAcceptFriendRequests());
            ps.setString(5, p.getStatus().name());
        });
    }
}
