package net.rustcore.duel.stats;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.rustcore.duel.db.Migrations;
import net.rustcore.duel.db.dao.StatsDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that StatsManager.recordResult() produces correct values.
 * Uses H2 in-memory database, matching the existing test patterns.
 */
class StatsManagerConcurrencyTest {

    private DataSource ds;
    private StatsDao dao;
    private StatsManager mgr;

    @BeforeEach
    void setUp() throws Exception {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:h2:mem:stats_conc;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        hc.setUsername("sa");
        hc.setPassword("");
        hc.setMaximumPoolSize(2);
        ds = new HikariDataSource(hc);

        // Create tables via Migrations
        new Migrations(ds).apply();

        dao = new StatsDao(ds);
        mgr = StatsManager.forTest(dao);
        mgr.registerMode("test");
    }

    @AfterEach
    void tearDown() {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("SHUTDOWN");
        } catch (Exception ignored) {}
    }

    @Test
    void recordResult_producesCorrectCacheCounts() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        for (int i = 0; i < 100; i++) {
            mgr.recordResult("test", a, b);
        }

        PlayerStats statsA = mgr.getStats("test", a);
        assertEquals(100, statsA.getWins(), "player A should have 100 wins");
        assertEquals(100, statsA.getWinStreak());
        assertEquals(100, statsA.getBestWinStreak());

        PlayerStats statsB = mgr.getStats("test", b);
        assertEquals(100, statsB.getLosses(), "player B should have 100 losses");
        assertEquals(0, statsB.getWinStreak());
    }
}