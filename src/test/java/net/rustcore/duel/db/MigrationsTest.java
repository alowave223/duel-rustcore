package net.rustcore.duel.db;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationsTest {
    @Test
    void appliesAllMigrationsAndIsIdempotent() throws Exception {
        Database db = Database.forJdbc("jdbc:h2:mem:mig;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "", 2);
        try {
            new Migrations(db.dataSource()).apply();
            new Migrations(db.dataSource()).apply(); // second call: no-op

            try (Connection c = db.dataSource().getConnection();
                 Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT MAX(version) FROM schema_version")) {
                assertTrue(rs.next());
                assertEquals(5, rs.getInt(1));
            }
        } finally {
            db.shutdown();
        }
    }

    @Test
    void appliesV005RatingColumnsAndIndex() throws Exception {
        Database db = Database.forJdbc("jdbc:h2:mem:mig_v005;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "", 2);
        try {
            new Migrations(db.dataSource()).apply();

            try (Connection c = db.dataSource().getConnection()) {
                assertColumnsExist(c, "duels_stats", "mu", "sigma", "rating_ordinal", "matches_rated");
                assertIndexExists(c, "duels_stats", "idx_stats_mode_ordinal");
            }
        } finally {
            db.shutdown();
        }
    }

    private static void assertColumnsExist(Connection c, String table, String... expected) throws Exception {
        DatabaseMetaData md = c.getMetaData();
        Set<String> columns = new TreeSet<>();
        try (ResultSet rs = md.getColumns(null, null, table, null)) {
            while (rs.next()) columns.add(rs.getString("COLUMN_NAME").toLowerCase());
        }
        for (String column : expected) {
            assertTrue(columns.contains(column), "Missing column " + column);
        }
    }

    private static void assertIndexExists(Connection c, String table, String expected) throws Exception {
        DatabaseMetaData md = c.getMetaData();
        Set<String> indexes = new TreeSet<>();
        try (ResultSet rs = md.getIndexInfo(null, null, table, false, false)) {
            while (rs.next()) {
                String index = rs.getString("INDEX_NAME");
                if (index != null) indexes.add(index.toLowerCase());
            }
        }
        assertTrue(indexes.contains(expected), "Missing index " + expected);
    }
}
