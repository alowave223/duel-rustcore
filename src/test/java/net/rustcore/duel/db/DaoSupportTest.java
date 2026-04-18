package net.rustcore.duel.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DaoSupportTest {
    private Database db;

    @BeforeEach
    void setup() throws Exception {
        db = Database.forJdbc("jdbc:h2:mem:daos;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "", 2);
        try (Connection c = db.dataSource().getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DROP TABLE IF EXISTS t");
            st.executeUpdate("CREATE TABLE t (k INT PRIMARY KEY, v INT NOT NULL)");
        }
    }

    @AfterEach
    void tearDown() { db.shutdown(); }

    @Test
    void withTxCommitsOnSuccess() throws Exception {
        DaoSupport sup = new DaoSupport(db.dataSource());
        sup.withTx(c -> {
            try (var ps = c.prepareStatement("INSERT INTO t(k,v) VALUES (1, 10)")) { ps.executeUpdate(); }
            return null;
        });
        int v = sup.queryOne("SELECT v FROM t WHERE k=1", rs -> rs.getInt(1)).orElseThrow();
        assertEquals(10, v);
    }

    @Test
    void withTxRollsBackOnThrow() throws Exception {
        DaoSupport sup = new DaoSupport(db.dataSource());
        try {
            sup.withTx(c -> {
                try (var ps = c.prepareStatement("INSERT INTO t(k,v) VALUES (2, 20)")) { ps.executeUpdate(); }
                throw new RuntimeException("boom");
            });
        } catch (RuntimeException expected) {
            // expected
        }
        var rows = sup.queryList("SELECT v FROM t WHERE k=2", null, rs -> rs.getInt(1));
        assertTrue(rows.isEmpty(), "row must have been rolled back");
    }
}
