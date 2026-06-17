package net.rustcore.duel.db;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseConfigTest {
    @Test
    void readsFromConfigSection() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("database.host", "db.example.com");
        y.set("database.port", 3307);
        y.set("database.name", "duels");
        y.set("database.user", "dueluser");
        y.set("database.password", "secret");
        y.set("database.pool-size", 6);
        y.set("database.use-ssl", true);

        DatabaseConfig c = DatabaseConfig.fromSection(y.getConfigurationSection("database"));

        assertEquals("db.example.com", c.host());
        assertEquals(3307, c.port());
        assertEquals("duels", c.database());
        assertEquals("dueluser", c.user());
        assertEquals("secret", c.password());
        assertEquals(6, c.poolSize());
        assertTrue(c.useSsl());
        assertTrue(c.jdbcUrl().startsWith("jdbc:mysql://db.example.com:3307/duels"));
    }

    @Test
    void fromSection_usesDefaults_whenKeysMissing() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("database.host", "only-host");
        y.set("database.password", "test123");

        DatabaseConfig c = DatabaseConfig.fromSection(y.getConfigurationSection("database"));

        assertEquals("only-host", c.host());
        assertEquals(3306, c.port());
        assertEquals("duels", c.database());
        assertEquals("root", c.user());
        assertEquals("test123", c.password());
        assertEquals(8, c.poolSize());
        assertFalse(c.useSsl());
    }

    @Test
    void fromSection_rejectsEmptyPassword() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("database.password", "");

        try {
            DatabaseConfig.fromSection(y.getConfigurationSection("database"));
            fail("should have thrown");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("password"),
                    "expected 'password' in error message, got: " + expected.getMessage());
        }
    }
}