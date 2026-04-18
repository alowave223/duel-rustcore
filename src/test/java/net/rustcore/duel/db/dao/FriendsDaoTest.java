package net.rustcore.duel.db.dao;

import net.rustcore.duel.db.Database;
import net.rustcore.duel.db.Migrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FriendsDaoTest {
    private Database db;

    @BeforeEach
    void setup() throws Exception {
        db = Database.forJdbc("jdbc:h2:mem:fd;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "", 2);
        new Migrations(db.dataSource()).apply();
    }

    @AfterEach
    void tearDown() { db.shutdown(); }

    @Test
    void addAndRemoveIsBidirectional() {
        FriendsDao dao = new FriendsDao(db.dataSource());
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        dao.addFriendPair(a, b);
        Set<UUID> fa = dao.loadFriends(a);
        Set<UUID> fb = dao.loadFriends(b);
        assertTrue(fa.contains(b));
        assertTrue(fb.contains(a));

        dao.removeFriendPair(a, b);
        assertFalse(dao.loadFriends(a).contains(b));
        assertFalse(dao.loadFriends(b).contains(a));
    }

    @Test
    void addIsIdempotent() {
        FriendsDao dao = new FriendsDao(db.dataSource());
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        dao.addFriendPair(a, b);
        dao.addFriendPair(a, b); // no exception
        assertTrue(dao.loadFriends(a).contains(b));
    }
}
