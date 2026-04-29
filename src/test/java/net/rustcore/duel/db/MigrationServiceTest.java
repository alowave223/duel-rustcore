package net.rustcore.duel.db;

import net.rustcore.duel.db.dao.FriendsDao;
import net.rustcore.duel.db.dao.KitLayoutsDao;
import net.rustcore.duel.db.dao.RankedPrefsDao;
import net.rustcore.duel.db.dao.SettingsDao;
import net.rustcore.duel.db.dao.StatsDao;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationServiceTest {
    private Database db;
    private Path tmp;

    @BeforeEach
    void setup() throws Exception {
        db = Database.forJdbc("jdbc:h2:mem:ms;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "", 2);
        new Migrations(db.dataSource()).apply();
        tmp = Files.createTempDirectory("duels-migrate");
    }

    @AfterEach
    void tearDown() throws Exception {
        db.shutdown();
        Files.walk(tmp).sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    }

    @Test
    void importsStatsFileIntoDb() throws Exception {
        UUID u = UUID.randomUUID();
        File statsDir = new File(tmp.toFile(), "stats");
        statsDir.mkdirs();
        File f = new File(statsDir, "nodebuff_stats.yml");
        YamlConfiguration y = new YamlConfiguration();
        y.set("players." + u + ".wins", 5);
        y.set("players." + u + ".losses", 2);
        y.save(f);

        MigrationService svc = new MigrationService(
                tmp.toFile(),
                new StatsDao(db.dataSource()),
                new FriendsDao(db.dataSource()),
                new SettingsDao(db.dataSource()),
                new KitLayoutsDao(db.dataSource()),
                new RankedPrefsDao(db.dataSource()));
        svc.runIfNeeded(java.util.List.of("nodebuff"));

        assertEquals(5, new StatsDao(db.dataSource()).loadAll("nodebuff").get(u).getWins());
        assertTrue(new File(statsDir, "nodebuff_stats.yml.migrated").exists());
    }
}
