package net.rustcore.duel.kit;

import net.rustcore.duel.db.Database;
import net.rustcore.duel.db.Migrations;
import net.rustcore.duel.db.dao.KitLayoutsDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KitLayoutManagerDbTest {
    private Database db;

    @BeforeEach
    void setup() throws Exception {
        db = Database.forJdbc("jdbc:h2:mem:klm;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "", 2);
        new Migrations(db.dataSource()).apply();
    }

    @AfterEach
    void tearDown() { db.shutdown(); }

    @Test
    void setLayoutPersistsAndResetDeletes() {
        KitLayoutsDao dao = new KitLayoutsDao(db.dataSource());
        KitLayoutManager mgr = KitLayoutManager.forTest(dao);
        UUID u = UUID.randomUUID();
        Map<Integer, Integer> raw = new HashMap<>();
        raw.put(0, 4);
        mgr.setLayout(u, "nd", new KitLayout(raw));

        KitLayoutManager fresh = KitLayoutManager.forTest(dao);
        KitLayout loaded = fresh.getLayout(u, "nd");
        assertEquals(4, loaded.getRaw().get(0));

        mgr.resetLayout(u, "nd");
        assertNull(KitLayoutManager.forTest(dao).getLayout(u, "nd"));
    }
}
