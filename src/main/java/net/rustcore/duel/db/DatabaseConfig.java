package net.rustcore.duel.db;

import org.bukkit.configuration.ConfigurationSection;

public record DatabaseConfig(
        String host, int port, String database, String user, String password,
        int poolSize, boolean useSsl
) {
    public static DatabaseConfig fromSection(ConfigurationSection s) {
        if (s == null) throw new IllegalStateException("Missing 'database' section in config.yml");
        return new DatabaseConfig(
                s.getString("host", "localhost"),
                s.getInt("port", 3306),
                s.getString("name", "duels"),
                s.getString("user", "root"),
                s.getString("password", ""),
                s.getInt("pool-size", 8),
                s.getBoolean("use-ssl", false)
        );
    }

    public String jdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8mb4"
                + "&connectionCollation=utf8mb4_unicode_ci"
                + "&useSSL=" + useSsl
                + "&serverTimezone=UTC"
                + "&rewriteBatchedStatements=true";
    }
}
