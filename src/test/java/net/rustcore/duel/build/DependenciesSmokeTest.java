package net.rustcore.duel.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DependenciesSmokeTest {
    @Test
    void hikariClassLoads() throws Exception {
        Class<?> c = Class.forName("com.zaxxer.hikari.HikariDataSource");
        assertNotNull(c);
    }

    @Test
    void mysqlDriverClassLoads() throws Exception {
        Class<?> c = Class.forName("com.mysql.cj.jdbc.Driver");
        assertNotNull(c);
    }

    @Test
    void h2DriverClassLoads() throws Exception {
        Class<?> c = Class.forName("org.h2.Driver");
        assertNotNull(c);
    }
}
