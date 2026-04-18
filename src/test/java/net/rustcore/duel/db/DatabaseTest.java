package net.rustcore.duel.db;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTest {
    @Test
    void canBorrowConnectionFromH2() throws Exception {
        Database db = Database.forJdbc("jdbc:h2:mem:dbt;MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "", 2);
        try {
            DataSource ds = db.dataSource();
            try (Connection c = ds.getConnection()) {
                assertTrue(c.isValid(1));
            }
        } finally {
            db.shutdown();
        }
    }
}
