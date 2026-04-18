package net.rustcore.duel.db;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationsTest {
    @Test
    void appliesV001AndIsIdempotent() throws Exception {
        Database db = Database.forJdbc("jdbc:h2:mem:mig;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "", 2);
        try {
            new Migrations(db.dataSource()).apply();
            new Migrations(db.dataSource()).apply(); // second call: no-op

            try (Connection c = db.dataSource().getConnection();
                 Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT MAX(version) FROM schema_version")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        } finally {
            db.shutdown();
        }
    }
}
