package net.rustcore.duel.settings;

import net.rustcore.duel.db.Database;
import net.rustcore.duel.db.Migrations;
import net.rustcore.duel.db.dao.SettingsDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsManagerDbTest {
    private Database db;

    @BeforeEach
    void setup() throws Exception {
        db = Database.forJdbc("jdbc:h2:mem:sm;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "", 2);
        new Migrations(db.dataSource()).apply();
    }

    @AfterEach
    void tearDown() { db.shutdown(); }

    @Test
    void getSettingsLoadsDefaults_thenUpdatePersists() {
        SettingsDao dao = new SettingsDao(db.dataSource());
        SettingsManager sm = SettingsManager.forTest(dao);
        UUID u = UUID.randomUUID();
        PlayerSettings first = sm.getSettings(u);
        assertEquals(PlayerSettings.Status.ONLINE, first.getStatus());
        first.setStatus(PlayerSettings.Status.DO_NOT_DISTURB);
        sm.update(u, first);

        SettingsManager fresh = SettingsManager.forTest(dao);
        assertEquals(PlayerSettings.Status.DO_NOT_DISTURB, fresh.getSettings(u).getStatus());
    }
}
