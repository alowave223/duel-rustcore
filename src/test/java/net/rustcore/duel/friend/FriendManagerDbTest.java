package net.rustcore.duel.friend;

import net.rustcore.duel.db.Database;
import net.rustcore.duel.db.Migrations;
import net.rustcore.duel.db.dao.FriendsDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FriendManagerDbTest {
    private Database db;

    @BeforeEach
    void setup() throws Exception {
        db = Database.forJdbc("jdbc:h2:mem:fm;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "", 2);
        new Migrations(db.dataSource()).apply();
    }

    @AfterEach
    void tearDown() { db.shutdown(); }

    @Test
    void addIsBidirectionalAndPersists() {
        FriendsDao dao = new FriendsDao(db.dataSource());
        FriendManager fm = FriendManager.forTest(dao);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        fm.ensureLoaded(a); fm.ensureLoaded(b);
        fm.addFriend(a, b);
        assertTrue(fm.isFriend(a, b));
        assertTrue(fm.isFriend(b, a));

        FriendManager fresh = FriendManager.forTest(dao);
        fresh.ensureLoaded(a);
        assertTrue(fresh.isFriend(a, b));

        fm.removeFriend(a, b);
        assertFalse(fm.isFriend(a, b));
    }
}
