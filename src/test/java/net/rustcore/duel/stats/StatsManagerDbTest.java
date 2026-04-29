package net.rustcore.duel.stats;

import net.rustcore.duel.db.Database;
import net.rustcore.duel.db.Migrations;
import net.rustcore.duel.db.dao.StatsDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatsManagerDbTest {
    private Database db;

    @BeforeEach
    void setup() throws Exception {
        db = Database.forJdbc("jdbc:h2:mem:smdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "", 2);
        new Migrations(db.dataSource()).apply();
    }

    @AfterEach
    void tearDown() { db.shutdown(); }

    @Test
    void recordResultThenFlushPersists() {
        StatsDao dao = new StatsDao(db.dataSource());
        StatsManager sm = StatsManager.forTest(dao);
        sm.registerMode("nodebuff");
        UUID w = UUID.randomUUID(), l = UUID.randomUUID();
        sm.recordResult("nodebuff", w, l);
        sm.flushAll();

        var reloaded = dao.loadAll("nodebuff");
        assertEquals(1, reloaded.get(w).getWins());
        assertEquals(1, reloaded.get(l).getLosses());
    }
}
