package net.rustcore.duel.duel;

import net.rustcore.duel.db.Database;
import net.rustcore.duel.db.Migrations;
import net.rustcore.duel.db.dao.RankedPrefsDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RankedPrefDbTest {
    private Database db;

    @BeforeEach
    void setup() throws Exception {
        db = Database.forJdbc("jdbc:h2:mem:rpd;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "", 2);
        new Migrations(db.dataSource()).apply();
    }

    @AfterEach
    void tearDown() { db.shutdown(); }

    @Test
    void rankedPreferenceRoundTrips() {
        RankedPrefsDao dao = new RankedPrefsDao(db.dataSource());
        UUID u = UUID.randomUUID();
        RankedPreferenceStore store = new RankedPreferenceStore(dao);
        assertFalse(store.isRanked(u));
        store.setRanked(u, true);
        assertTrue(store.isRanked(u));
        RankedPreferenceStore fresh = new RankedPreferenceStore(dao);
        assertTrue(fresh.isRanked(u));
    }
}
