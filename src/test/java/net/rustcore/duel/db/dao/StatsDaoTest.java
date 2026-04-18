package net.rustcore.duel.db.dao;

import net.rustcore.duel.db.Database;
import net.rustcore.duel.db.Migrations;
import net.rustcore.duel.stats.PlayerStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatsDaoTest {
    private Database db;

    @BeforeEach
    void setup() throws Exception {
        db = Database.forJdbc("jdbc:h2:mem:sd;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "", 2);
        new Migrations(db.dataSource()).apply();
    }

    @AfterEach
    void tearDown() { db.shutdown(); }

    @Test
    void upsertAndLoadAll() {
        StatsDao dao = new StatsDao(db.dataSource());
        UUID u = UUID.randomUUID();
        PlayerStats s = new PlayerStats();
        s.setWins(3); s.setLosses(2); s.setKills(5); s.setDeaths(1);
        s.setWinStreak(1); s.setBestWinStreak(4); s.setElo(1100);

        dao.upsert("nodebuff", u, s);

        Map<UUID, PlayerStats> loaded = dao.loadAll("nodebuff");
        PlayerStats got = loaded.get(u);
        assertEquals(3, got.getWins());
        assertEquals(1100, got.getElo());
        assertEquals(4, got.getBestWinStreak());
    }

    @Test
    void upsertReplaces() {
        StatsDao dao = new StatsDao(db.dataSource());
        UUID u = UUID.randomUUID();
        PlayerStats s1 = new PlayerStats(); s1.setWins(1); s1.setElo(1000);
        dao.upsert("m", u, s1);
        PlayerStats s2 = new PlayerStats(); s2.setWins(7); s2.setElo(1234);
        dao.upsert("m", u, s2);
        assertEquals(7, dao.loadAll("m").get(u).getWins());
        assertEquals(1234, dao.loadAll("m").get(u).getElo());
    }
}
