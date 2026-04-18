package net.rustcore.duel.db.dao;

import net.rustcore.duel.db.Database;
import net.rustcore.duel.db.Migrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitLayoutsDaoTest {
    private Database db;

    @BeforeEach
    void setup() throws Exception {
        db = Database.forJdbc("jdbc:h2:mem:kl;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "", 2);
        new Migrations(db.dataSource()).apply();
    }

    @AfterEach
    void tearDown() { db.shutdown(); }

    @Test
    void upsertReplacesFullLayout() {
        KitLayoutsDao dao = new KitLayoutsDao(db.dataSource());
        UUID u = UUID.randomUUID();
        Map<Integer, Integer> first = new LinkedHashMap<>();
        first.put(0, 4); first.put(1, 5);
        dao.upsertLayout(u, "nodebuff", first);

        Map<Integer, Integer> second = new LinkedHashMap<>();
        second.put(0, 7);
        dao.upsertLayout(u, "nodebuff", second);

        Map<String, Map<Integer, Integer>> loaded = dao.load(u);
        assertEquals(1, loaded.get("nodebuff").size());
        assertEquals(7, loaded.get("nodebuff").get(0));
    }

    @Test
    void deleteLayoutClearsMode() {
        KitLayoutsDao dao = new KitLayoutsDao(db.dataSource());
        UUID u = UUID.randomUUID();
        Map<Integer, Integer> m = new LinkedHashMap<>();
        m.put(0, 1);
        dao.upsertLayout(u, "m1", m);
        dao.deleteLayout(u, "m1");
        assertTrue(dao.load(u).getOrDefault("m1", Map.of()).isEmpty());
    }
}
