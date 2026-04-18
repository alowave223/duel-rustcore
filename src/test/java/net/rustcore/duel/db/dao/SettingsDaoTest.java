package net.rustcore.duel.db.dao;

import net.rustcore.duel.db.Database;
import net.rustcore.duel.db.Migrations;
import net.rustcore.duel.settings.PlayerSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsDaoTest {
    private Database db;

    @BeforeEach
    void setup() throws Exception {
        db = Database.forJdbc("jdbc:h2:mem:st;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "", 2);
        new Migrations(db.dataSource()).apply();
    }

    @AfterEach
    void tearDown() { db.shutdown(); }

    @Test
    void upsertAndLoad() {
        SettingsDao dao = new SettingsDao(db.dataSource());
        UUID u = UUID.randomUUID();
        PlayerSettings s = new PlayerSettings();
        s.setWhoCanInviteToParty(PlayerSettings.Visibility.FRIENDS_ONLY);
        s.setWhoCanChallenge(PlayerSettings.Visibility.NOBODY);
        s.setAcceptFriendRequests(false);
        s.setStatus(PlayerSettings.Status.DO_NOT_DISTURB);

        dao.upsert(u, s);
        PlayerSettings loaded = dao.load(u).orElseThrow();

        assertEquals(PlayerSettings.Visibility.FRIENDS_ONLY, loaded.getWhoCanInviteToParty());
        assertEquals(PlayerSettings.Visibility.NOBODY, loaded.getWhoCanChallenge());
        assertEquals(false, loaded.isAcceptFriendRequests());
        assertEquals(PlayerSettings.Status.DO_NOT_DISTURB, loaded.getStatus());
    }
}
