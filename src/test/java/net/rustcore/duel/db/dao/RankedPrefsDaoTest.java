package net.rustcore.duel.db.dao;

import net.rustcore.duel.db.Database;
import net.rustcore.duel.db.Migrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankedPrefsDaoTest {
    private Database db;

    @BeforeEach
    void setup() throws Exception {
        db = Database.forJdbc("jdbc:h2:mem:rp;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "", 2);
        new Migrations(db.dataSource()).apply();
    }

    @AfterEach
    void tearDown() { db.shutdown(); }

    @Test
    void upsertAndLoadAll() {
        RankedPrefsDao dao = new RankedPrefsDao(db.dataSource());
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        dao.upsert(a, true);
        dao.upsert(b, false);
        dao.upsert(a, false);
        Map<UUID, Boolean> all = dao.loadAll();
        assertEquals(2, all.size());
        assertEquals(false, all.get(a));
        assertTrue(!all.get(b));
    }
}
