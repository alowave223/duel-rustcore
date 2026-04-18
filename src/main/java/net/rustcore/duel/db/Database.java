package net.rustcore.duel.db;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public final class Database {

    private final HikariDataSource ds;

    private Database(HikariDataSource ds) { this.ds = ds; }

    public static Database forConfig(DatabaseConfig c) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(c.jdbcUrl());
        hc.setUsername(c.user());
        hc.setPassword(c.password());
        hc.setMaximumPoolSize(Math.max(2, c.poolSize()));
        hc.setPoolName("duels-hikari");
        hc.setConnectionTimeout(5_000);
        hc.setValidationTimeout(3_000);
        hc.setLeakDetectionThreshold(10_000);
        hc.addDataSourceProperty("cachePrepStmts", "true");
        hc.addDataSourceProperty("prepStmtCacheSize", "250");
        hc.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        return new Database(new HikariDataSource(hc));
    }

    public static Database forJdbc(String url, String user, String pass, int poolSize) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(url);
        hc.setUsername(user);
        hc.setPassword(pass);
        hc.setMaximumPoolSize(poolSize);
        hc.setPoolName("duels-hikari-test");
        return new Database(new HikariDataSource(hc));
    }

    public DataSource dataSource() { return ds; }

    public void shutdown() { if (!ds.isClosed()) ds.close(); }
}
