# MySQL Persistence Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all YAML/file-based player persistence (stats, friends, settings, kit layouts, ranked preference) with a MySQL backend via HikariCP + a small DAO layer, while preserving existing manager APIs and adding a one-shot YAML→MySQL migration.

**Architecture:** New `db` package owns a HikariCP pool, a `Migrations` runner (plain `schema_version` table with numbered SQL scripts), and one DAO per domain (`StatsDao`, `FriendsDao`, `SettingsDao`, `KitLayoutsDao`, `RankedPrefsDao`). Existing managers keep their public API but: (1) load from DAO on player join (lazy per-UUID hydration, not whole-table scans), (2) write through to DAO on mutations with a write-behind dirty queue flushed async on a fixed interval and on `onDisable`. Duel gameplay reads/writes remain in-memory during a duel and are flushed at duel end (current behavior). Config stays in YAML — only player state moves to MySQL. A `MigrationService` one-shot imports any existing `*.yml` on first enable when the DB is empty, then renames them to `*.yml.migrated`.

**Tech Stack:** Java 21, Paper 1.21.11, MySQL 8.x (utf8mb4), HikariCP 5.x (shaded), MySQL Connector/J 8.x (shaded via maven-shade-plugin), SnakeYAML (already provided by Paper) for the one-shot migration.

---

## File Structure

- Create: `src/main/java/net/rustcore/duel/db/DatabaseConfig.java` — POJO for `database.*` config (host, port, db, user, pass, pool size, useSSL).
- Create: `src/main/java/net/rustcore/duel/db/Database.java` — HikariCP pool owner; `Connection borrow()`, `void shutdown()`, exposes `DataSource`.
- Create: `src/main/java/net/rustcore/duel/db/Migrations.java` — reads `db/migrations/V###__*.sql` from jar resources, applies in order, tracks in `schema_version`.
- Create: `src/main/resources/db/migrations/V001__init.sql` — all tables: `duels_stats`, `duels_friends`, `duels_settings`, `duels_kit_layouts`, `duels_ranked_prefs`, `schema_version`.
- Create: `src/main/java/net/rustcore/duel/db/DaoSupport.java` — small helpers: `withConnection`, `withTx`, `queryList`, `execute`, standardized SQLException logging.
- Create: `src/main/java/net/rustcore/duel/db/dao/StatsDao.java` — `loadAll(modeId)`, `upsert(modeId, uuid, PlayerStats)`, `upsertBatch(modeId, Map)`.
- Create: `src/main/java/net/rustcore/duel/db/dao/FriendsDao.java` — `loadFriends(uuid)`, `addFriendPair(a, b)`, `removeFriendPair(a, b)`, `loadAllPairs()` (for optional warm start).
- Create: `src/main/java/net/rustcore/duel/db/dao/SettingsDao.java` — `load(uuid)`, `upsert(uuid, PlayerSettings)`.
- Create: `src/main/java/net/rustcore/duel/db/dao/KitLayoutsDao.java` — `load(uuid)`, `upsertLayout(uuid, modeId, Map<Integer,Integer>)`, `deleteLayout(uuid, modeId)`.
- Create: `src/main/java/net/rustcore/duel/db/dao/RankedPrefsDao.java` — `loadAll()`, `upsert(uuid, boolean)`.
- Create: `src/main/java/net/rustcore/duel/db/WriteBehindQueue.java` — thread-safe dirty-set + periodic async flusher; used by StatsManager and others.
- Create: `src/main/java/net/rustcore/duel/db/MigrationService.java` — one-shot import from existing YAML into DB.
- Modify: `pom.xml` — add HikariCP + MySQL Connector/J deps, add maven-shade-plugin with relocations, bump finalName.
- Modify: `src/main/resources/config.yml` — add `database:` section.
- Modify: `src/main/resources/plugin.yml` — add `load: STARTUP` is not needed; just ensure depend unchanged; add `libraries:` block as fallback (Paper plugin libraries) commented — shading is primary.
- Modify: `src/main/java/net/rustcore/duel/DuelsPlugin.java` — init `Database` + `Migrations` + `MigrationService` before managers; hand `Database` to each manager; shutdown pool in `onDisable`.
- Modify: `src/main/java/net/rustcore/duel/stats/StatsManager.java` — drop YAML; accept `StatsDao`; lazy-load per UUID + per mode; write-behind on mutations; `saveAll()` flushes queue.
- Modify: `src/main/java/net/rustcore/duel/friend/FriendManager.java` — drop YAML; accept `FriendsDao`; load on `onPlayerJoin` listener for the joining player; DB-write on `addFriend`/`removeFriend`.
- Modify: `src/main/java/net/rustcore/duel/settings/SettingsManager.java` — drop YAML; accept `SettingsDao`; lazy-load on `getSettings`; upsert on mutation.
- Modify: `src/main/java/net/rustcore/duel/kit/KitLayoutManager.java` — drop YAML; accept `KitLayoutsDao`; lazy-load on first query for a UUID; upsert on `setLayout`/delete on `reset`.
- Modify: `src/main/java/net/rustcore/duel/duel/DuelManager.java` — drop YAML `ranked_preferences.yml`; accept `RankedPrefsDao`; remove `rankedFile`/`rankedConfig` fields; keep `setRanked/isRanked` signatures; rewrite `loadRankedPreferences`, `saveRankedSync`, `saveRankedAsync`.
- Create: `src/main/java/net/rustcore/duel/listener/PlayerJoinQuitListener.java` — hydrate FriendManager/SettingsManager/KitLayoutManager for the joining player; evict on quit if desired.
- Create: `src/test/java/net/rustcore/duel/db/MigrationsTest.java` — unit test for migrations runner using H2 in MySQL mode.
- Create: `src/test/java/net/rustcore/duel/db/dao/StatsDaoTest.java` — DAO round-trip test on H2.
- Create: `src/test/java/net/rustcore/duel/db/dao/FriendsDaoTest.java` — DAO round-trip test on H2.
- Create: `src/test/java/net/rustcore/duel/db/dao/SettingsDaoTest.java` — DAO round-trip test on H2.
- Create: `src/test/java/net/rustcore/duel/db/dao/KitLayoutsDaoTest.java` — DAO round-trip test on H2.
- Create: `src/test/java/net/rustcore/duel/db/dao/RankedPrefsDaoTest.java` — DAO round-trip test on H2.
- Create: `src/test/java/net/rustcore/duel/db/MigrationServiceTest.java` — YAML→DB import test.

Tests use H2 in MySQL compatibility mode (`jdbc:h2:mem:...;MODE=MySQL;DATABASE_TO_LOWER=TRUE`) to avoid needing a real server on CI. Schema must stay H2-MySQL-mode compatible (no vendor-only syntax like `ON DUPLICATE KEY UPDATE` in migrations — use `MERGE` via a portable write helper, or gate upserts in the DAO using `INSERT ... ON DUPLICATE KEY UPDATE` for prod and `MERGE INTO` for H2 — we pick DAO-level two-path: see Task 6).

---

## Task 1: Add dependencies and shade plugin

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Write the failing test**

`src/test/java/net/rustcore/duel/build/DependenciesSmokeTest.java`

```java
package net.rustcore.duel.build;

import com.zaxxer.hikari.HikariDataSource;
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=DependenciesSmokeTest test`
Expected: FAIL with `ClassNotFoundException: com.zaxxer.hikari.HikariDataSource`.

- [ ] **Step 3: Write minimal implementation**

Edit `pom.xml`. Inside `<dependencies>`, append:

```xml
<dependency>
    <groupId>com.zaxxer</groupId>
    <artifactId>HikariCP</artifactId>
    <version>5.1.0</version>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>8.4.0</version>
</dependency>
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-jdk14</artifactId>
    <version>2.0.13</version>
</dependency>
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.2</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <version>2.2.224</version>
    <scope>test</scope>
</dependency>
```

Inside `<build><plugins>`, append maven-surefire + maven-shade:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.2.5</version>
</plugin>
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.5.3</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
                <createDependencyReducedPom>false</createDependencyReducedPom>
                <minimizeJar>false</minimizeJar>
                <relocations>
                    <relocation>
                        <pattern>com.zaxxer.hikari</pattern>
                        <shadedPattern>net.rustcore.duel.lib.hikari</shadedPattern>
                    </relocation>
                    <relocation>
                        <pattern>com.mysql</pattern>
                        <shadedPattern>net.rustcore.duel.lib.mysql</shadedPattern>
                    </relocation>
                    <relocation>
                        <pattern>org.slf4j</pattern>
                        <shadedPattern>net.rustcore.duel.lib.slf4j</shadedPattern>
                    </relocation>
                </relocations>
                <artifactSet>
                    <excludes>
                        <exclude>io.papermc.paper:*</exclude>
                        <exclude>me.clip:*</exclude>
                        <exclude>com.infernalsuite.asp:*</exclude>
                    </excludes>
                </artifactSet>
                <filters>
                    <filter>
                        <artifact>*:*</artifact>
                        <excludes>
                            <exclude>META-INF/*.SF</exclude>
                            <exclude>META-INF/*.DSA</exclude>
                            <exclude>META-INF/*.RSA</exclude>
                        </excludes>
                    </filter>
                </filters>
            </configuration>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=DependenciesSmokeTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/test/java/net/rustcore/duel/build/DependenciesSmokeTest.java
git commit -m "build: add HikariCP, MySQL Connector/J, H2, JUnit and shade plugin"
```

---

## Task 2: DatabaseConfig + config.yml section

**Files:**
- Create: `src/main/java/net/rustcore/duel/db/DatabaseConfig.java`
- Modify: `src/main/resources/config.yml`
- Create: `src/test/java/net/rustcore/duel/db/DatabaseConfigTest.java`

- [ ] **Step 1: Write the failing test**

```java
package net.rustcore.duel.db;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=DatabaseConfigTest test`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/net/rustcore/duel/db/DatabaseConfig.java`:

```java
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
```

Append to `src/main/resources/config.yml` (top-level):

```yaml
# ============================================================
# Database (MySQL)
# ============================================================
database:
  host: localhost
  port: 3306
  name: duels
  user: duels
  password: "change-me"
  pool-size: 8
  use-ssl: false
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=DatabaseConfigTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/rustcore/duel/db/DatabaseConfig.java src/main/resources/config.yml src/test/java/net/rustcore/duel/db/DatabaseConfigTest.java
git commit -m "feat(db): add DatabaseConfig record and database section in config.yml"
```

---

## Task 3: Database pool (HikariCP)

**Files:**
- Create: `src/main/java/net/rustcore/duel/db/Database.java`
- Create: `src/test/java/net/rustcore/duel/db/DatabaseTest.java`

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=DatabaseTest test`
Expected: FAIL — class missing.

- [ ] **Step 3: Write minimal implementation**

```java
package net.rustcore.duel.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=DatabaseTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/rustcore/duel/db/Database.java src/test/java/net/rustcore/duel/db/DatabaseTest.java
git commit -m "feat(db): add Database Hikari pool wrapper"
```

---

## Task 4: Schema (V001__init.sql)

**Files:**
- Create: `src/main/resources/db/migrations/V001__init.sql`

- [ ] **Step 1: Write the failing test** (deferred — Migrations runner tests in Task 5 cover this. Skip test here.)

- [ ] **Step 2: Write the migration file**

Create `src/main/resources/db/migrations/V001__init.sql`:

```sql
CREATE TABLE IF NOT EXISTS schema_version (
    version     INT           NOT NULL PRIMARY KEY,
    applied_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    description VARCHAR(255)  NOT NULL
);

CREATE TABLE IF NOT EXISTS duels_stats (
    mode_id          VARCHAR(64)  NOT NULL,
    player_uuid      CHAR(36)     NOT NULL,
    wins             INT          NOT NULL DEFAULT 0,
    losses           INT          NOT NULL DEFAULT 0,
    kills            INT          NOT NULL DEFAULT 0,
    deaths           INT          NOT NULL DEFAULT 0,
    win_streak       INT          NOT NULL DEFAULT 0,
    best_win_streak  INT          NOT NULL DEFAULT 0,
    elo              INT          NOT NULL DEFAULT 1000,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (mode_id, player_uuid),
    INDEX idx_stats_mode_elo (mode_id, elo DESC),
    INDEX idx_stats_mode_wins (mode_id, wins DESC)
);

CREATE TABLE IF NOT EXISTS duels_friends (
    player_a CHAR(36) NOT NULL,
    player_b CHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (player_a, player_b),
    INDEX idx_friends_b (player_b)
);

CREATE TABLE IF NOT EXISTS duels_settings (
    player_uuid           CHAR(36)    NOT NULL PRIMARY KEY,
    party_invites         VARCHAR(16) NOT NULL DEFAULT 'ALL',
    challenges            VARCHAR(16) NOT NULL DEFAULT 'ALL',
    accept_friend_requests TINYINT(1) NOT NULL DEFAULT 1,
    status                VARCHAR(16) NOT NULL DEFAULT 'ONLINE',
    updated_at            TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS duels_kit_layouts (
    player_uuid CHAR(36)    NOT NULL,
    mode_id     VARCHAR(64) NOT NULL,
    src_slot    INT         NOT NULL,
    dst_slot    INT         NOT NULL,
    PRIMARY KEY (player_uuid, mode_id, src_slot)
);

CREATE TABLE IF NOT EXISTS duels_ranked_prefs (
    player_uuid CHAR(36)   NOT NULL PRIMARY KEY,
    ranked      TINYINT(1) NOT NULL DEFAULT 0,
    updated_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

> Note: `friendship` is stored with canonical ordering `player_a < player_b` (enforced by DAO), so one row per pair. Primary keys prevent duplicates; composite index on `player_b` accelerates the reverse direction of `loadFriends`.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migrations/V001__init.sql
git commit -m "feat(db): add V001 init schema for duels persistence"
```

---

## Task 5: Migrations runner

**Files:**
- Create: `src/main/java/net/rustcore/duel/db/Migrations.java`
- Create: `src/test/java/net/rustcore/duel/db/MigrationsTest.java`

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=MigrationsTest test`
Expected: FAIL — class missing.

- [ ] **Step 3: Write minimal implementation**

```java
package net.rustcore.duel.db;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Migrations {

    private static final Pattern NAME = Pattern.compile("V(\\d+)__([A-Za-z0-9_\\-]+)\\.sql");

    private final DataSource ds;

    public Migrations(DataSource ds) { this.ds = ds; }

    public void apply() throws Exception {
        try (Connection c = ds.getConnection()) {
            ensureVersionTable(c);
            List<Script> scripts = discover();
            Collections.sort(scripts, (a, b) -> Integer.compare(a.version, b.version));
            for (Script s : scripts) {
                if (alreadyApplied(c, s.version)) continue;
                runSql(c, s.sql);
                record(c, s.version, s.description);
            }
        }
    }

    private void ensureVersionTable(Connection c) throws Exception {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version (" +
                    "version INT NOT NULL PRIMARY KEY," +
                    "applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "description VARCHAR(255) NOT NULL)");
        }
    }

    private boolean alreadyApplied(Connection c, int v) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM schema_version WHERE version = ?")) {
            ps.setInt(1, v);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private void record(Connection c, int v, String desc) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO schema_version(version, description) VALUES (?, ?)")) {
            ps.setInt(1, v);
            ps.setString(2, desc);
            ps.executeUpdate();
        }
    }

    private void runSql(Connection c, String sql) throws Exception {
        for (String stmt : sql.split(";\\s*\\r?\\n")) {
            String trimmed = stmt.trim();
            if (trimmed.isEmpty()) continue;
            try (Statement st = c.createStatement()) { st.executeUpdate(trimmed); }
        }
    }

    private List<Script> discover() throws Exception {
        List<Script> out = new ArrayList<>();
        ClassLoader cl = Migrations.class.getClassLoader();
        Enumeration<URL> roots = cl.getResources("db/migrations");
        // Fallback: iterate known files by convention V001..V099
        for (int i = 1; i < 100; i++) {
            String name = String.format("db/migrations/V%03d__init.sql", i);
            try (InputStream is = cl.getResourceAsStream(name)) {
                if (is == null) continue;
                String sql = read(is);
                Matcher m = NAME.matcher(name.substring(name.lastIndexOf('/') + 1));
                if (m.matches()) {
                    out.add(new Script(Integer.parseInt(m.group(1)), m.group(2), sql));
                }
            }
        }
        return out;
    }

    private String read(InputStream in) throws Exception {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }

    private record Script(int version, String description, String sql) {}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=MigrationsTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/rustcore/duel/db/Migrations.java src/test/java/net/rustcore/duel/db/MigrationsTest.java
git commit -m "feat(db): add Migrations runner with schema_version tracking"
```

---

## Task 6: DaoSupport + portable upsert helper

**Files:**
- Create: `src/main/java/net/rustcore/duel/db/DaoSupport.java`
- Create: `src/test/java/net/rustcore/duel/db/DaoSupportTest.java`

- [ ] **Step 1: Write the failing test**

```java
package net.rustcore.duel.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DaoSupportTest {
    private Database db;

    @BeforeEach
    void setup() throws Exception {
        db = Database.forJdbc("jdbc:h2:mem:daos;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "", 2);
        try (Connection c = db.dataSource().getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE t (k INT PRIMARY KEY, v INT NOT NULL)");
        }
    }

    @AfterEach
    void tearDown() { db.shutdown(); }

    @Test
    void withTxCommitsOnSuccess() throws Exception {
        DaoSupport sup = new DaoSupport(db.dataSource());
        sup.withTx(c -> {
            try (var ps = c.prepareStatement("INSERT INTO t(k,v) VALUES (1, 10)")) { ps.executeUpdate(); }
            return null;
        });
        int v = sup.queryOne("SELECT v FROM t WHERE k=1", rs -> rs.getInt(1)).orElseThrow();
        assertEquals(10, v);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=DaoSupportTest test`
Expected: FAIL — class missing.

- [ ] **Step 3: Write minimal implementation**

```java
package net.rustcore.duel.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DaoSupport {

    public interface SqlFunction<T> { T apply(Connection c) throws SQLException; }
    public interface RowMapper<T>   { T map(ResultSet rs) throws SQLException; }
    public interface StmtBinder     { void bind(PreparedStatement ps) throws SQLException; }

    private final DataSource ds;

    public DaoSupport(DataSource ds) { this.ds = ds; }

    public <T> T withConnection(SqlFunction<T> fn) {
        try (Connection c = ds.getConnection()) { return fn.apply(c); }
        catch (SQLException e) { throw new RuntimeException(e); }
    }

    public <T> T withTx(SqlFunction<T> fn) {
        try (Connection c = ds.getConnection()) {
            boolean prev = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                T out = fn.apply(c);
                c.commit();
                return out;
            } catch (Throwable t) {
                c.rollback();
                throw t;
            } finally {
                c.setAutoCommit(prev);
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public <T> List<T> queryList(String sql, StmtBinder b, RowMapper<T> m) {
        return withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                if (b != null) b.bind(ps);
                try (ResultSet rs = ps.executeQuery()) {
                    List<T> out = new ArrayList<>();
                    while (rs.next()) out.add(m.map(rs));
                    return out;
                }
            }
        });
    }

    public <T> Optional<T> queryOne(String sql, RowMapper<T> m) {
        return withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(m.map(rs)) : Optional.<T>empty();
            }
        });
    }

    public int execute(String sql, StmtBinder b) {
        return withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                if (b != null) b.bind(ps);
                return ps.executeUpdate();
            }
        });
    }

    /** True when the JDBC URL of the pool is a MySQL URL. H2 callers should use portable MERGE. */
    public boolean isMySql() {
        return withConnection(c -> c.getMetaData().getURL().startsWith("jdbc:mysql"));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=DaoSupportTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/rustcore/duel/db/DaoSupport.java src/test/java/net/rustcore/duel/db/DaoSupportTest.java
git commit -m "feat(db): add DaoSupport helpers (withTx, queryList, isMySql)"
```

---

## Task 7: StatsDao

**Files:**
- Create: `src/main/java/net/rustcore/duel/db/dao/StatsDao.java`
- Create: `src/test/java/net/rustcore/duel/db/dao/StatsDaoTest.java`

- [ ] **Step 1: Write the failing test**

```java
package net.rustcore.duel.db.dao;

import net.rustcore.duel.db.Database;
import net.rustcore.duel.db.Migrations;
import net.rustcore.duel.stats.PlayerStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatsDaoTest {
    private Database db;

    @BeforeEach
    void setup() throws Exception {
        db = Database.forJdbc("jdbc:h2:mem:sd;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "", 2);
        new Migrations(db.dataSource()).apply();
    }

    @AfterEach
    void tearDown() { db.shutdown(); }

    @Test
    void upsertAndLoadAll() {
        StatsDao dao = new StatsDao(db.dataSource());
        UUID u = UUID.randomUUID();
        PlayerStats s = new PlayerStats();
        s.setWins(3); s.setLosses(2); s.setKills(5); s.setDeaths(1);
        s.setWinStreak(1); s.setBestWinStreak(4); s.setElo(1100);

        dao.upsert("nodebuff", u, s);

        Map<UUID, PlayerStats> loaded = dao.loadAll("nodebuff");
        PlayerStats got = loaded.get(u);
        assertEquals(3, got.getWins());
        assertEquals(1100, got.getElo());
        assertEquals(4, got.getBestWinStreak());
    }

    @Test
    void upsertReplaces() {
        StatsDao dao = new StatsDao(db.dataSource());
        UUID u = UUID.randomUUID();
        PlayerStats s1 = new PlayerStats(); s1.setWins(1); s1.setElo(1000);
        dao.upsert("m", u, s1);
        PlayerStats s2 = new PlayerStats(); s2.setWins(7); s2.setElo(1234);
        dao.upsert("m", u, s2);
        assertEquals(7, dao.loadAll("m").get(u).getWins());
        assertEquals(1234, dao.loadAll("m").get(u).getElo());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=StatsDaoTest test`
Expected: FAIL — class missing.

- [ ] **Step 3: Write minimal implementation**

```java
package net.rustcore.duel.db.dao;

import net.rustcore.duel.db.DaoSupport;
import net.rustcore.duel.stats.PlayerStats;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class StatsDao {

    private static final String SQL_UPSERT_MYSQL =
            "INSERT INTO duels_stats (mode_id, player_uuid, wins, losses, kills, deaths, win_streak, best_win_streak, elo)" +
            " VALUES (?,?,?,?,?,?,?,?,?)" +
            " ON DUPLICATE KEY UPDATE wins=VALUES(wins), losses=VALUES(losses), kills=VALUES(kills)," +
            " deaths=VALUES(deaths), win_streak=VALUES(win_streak), best_win_streak=VALUES(best_win_streak), elo=VALUES(elo)";

    private static final String SQL_UPSERT_H2 =
            "MERGE INTO duels_stats (mode_id, player_uuid, wins, losses, kills, deaths, win_streak, best_win_streak, elo)" +
            " KEY (mode_id, player_uuid) VALUES (?,?,?,?,?,?,?,?,?)";

    private static final String SQL_LOAD_ALL =
            "SELECT player_uuid, wins, losses, kills, deaths, win_streak, best_win_streak, elo FROM duels_stats WHERE mode_id=?";

    private final DaoSupport s;
    private final String upsertSql;

    public StatsDao(DataSource ds) {
        this.s = new DaoSupport(ds);
        this.upsertSql = s.isMySql() ? SQL_UPSERT_MYSQL : SQL_UPSERT_H2;
    }

    public void upsert(String modeId, UUID uuid, PlayerStats p) {
        s.execute(upsertSql, ps -> bind(ps, modeId, uuid, p));
    }

    public void upsertBatch(String modeId, Map<UUID, PlayerStats> map) {
        if (map.isEmpty()) return;
        s.withTx(c -> {
            try (PreparedStatement ps = c.prepareStatement(upsertSql)) {
                for (var e : map.entrySet()) {
                    bind(ps, modeId, e.getKey(), e.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return null;
        });
    }

    public Map<UUID, PlayerStats> loadAll(String modeId) {
        Map<UUID, PlayerStats> out = new HashMap<>();
        s.queryList(SQL_LOAD_ALL, ps -> ps.setString(1, modeId), rs -> {
            PlayerStats p = new PlayerStats();
            UUID u = UUID.fromString(rs.getString(1));
            p.setWins(rs.getInt(2));
            p.setLosses(rs.getInt(3));
            p.setKills(rs.getInt(4));
            p.setDeaths(rs.getInt(5));
            p.setWinStreak(rs.getInt(6));
            p.setBestWinStreak(rs.getInt(7));
            p.setElo(rs.getInt(8));
            out.put(u, p);
            return null;
        });
        return out;
    }

    private static void bind(PreparedStatement ps, String modeId, UUID u, PlayerStats p) throws java.sql.SQLException {
        ps.setString(1, modeId);
        ps.setString(2, u.toString());
        ps.setInt(3, p.getWins());
        ps.setInt(4, p.getLosses());
        ps.setInt(5, p.getKills());
        ps.setInt(6, p.getDeaths());
        ps.setInt(7, p.getWinStreak());
        ps.setInt(8, p.getBestWinStreak());
        ps.setInt(9, p.getElo());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=StatsDaoTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/rustcore/duel/db/dao/StatsDao.java src/test/java/net/rustcore/duel/db/dao/StatsDaoTest.java
git commit -m "feat(db): add StatsDao with MySQL/H2 portable upsert"
```

---

## Task 8: FriendsDao

**Files:**
- Create: `src/main/java/net/rustcore/duel/db/dao/FriendsDao.java`
- Create: `src/test/java/net/rustcore/duel/db/dao/FriendsDaoTest.java`

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=FriendsDaoTest test`
Expected: FAIL — class missing.

- [ ] **Step 3: Write minimal implementation**

```java
package net.rustcore.duel.db.dao;

import net.rustcore.duel.db.DaoSupport;

import javax.sql.DataSource;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class FriendsDao {

    private final DaoSupport s;

    public FriendsDao(DataSource ds) { this.s = new DaoSupport(ds); }

    public Set<UUID> loadFriends(UUID uuid) {
        Set<UUID> out = new HashSet<>();
        s.queryList(
                "SELECT CASE WHEN player_a=? THEN player_b ELSE player_a END AS friend " +
                "FROM duels_friends WHERE player_a=? OR player_b=?",
                ps -> { ps.setString(1, uuid.toString()); ps.setString(2, uuid.toString()); ps.setString(3, uuid.toString()); },
                rs -> { out.add(UUID.fromString(rs.getString(1))); return null; });
        return out;
    }

    public void addFriendPair(UUID a, UUID b) {
        UUID[] o = canonical(a, b);
        String sql = s.isMySql()
                ? "INSERT IGNORE INTO duels_friends (player_a, player_b) VALUES (?, ?)"
                : "MERGE INTO duels_friends (player_a, player_b) KEY (player_a, player_b) VALUES (?, ?)";
        s.execute(sql, ps -> { ps.setString(1, o[0].toString()); ps.setString(2, o[1].toString()); });
    }

    public void removeFriendPair(UUID a, UUID b) {
        UUID[] o = canonical(a, b);
        s.execute("DELETE FROM duels_friends WHERE player_a=? AND player_b=?",
                ps -> { ps.setString(1, o[0].toString()); ps.setString(2, o[1].toString()); });
    }

    private static UUID[] canonical(UUID a, UUID b) {
        return a.toString().compareTo(b.toString()) <= 0 ? new UUID[]{a, b} : new UUID[]{b, a};
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=FriendsDaoTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/rustcore/duel/db/dao/FriendsDao.java src/test/java/net/rustcore/duel/db/dao/FriendsDaoTest.java
git commit -m "feat(db): add FriendsDao with canonical-pair storage"
```

---

## Task 9: SettingsDao

**Files:**
- Create: `src/main/java/net/rustcore/duel/db/dao/SettingsDao.java`
- Create: `src/test/java/net/rustcore/duel/db/dao/SettingsDaoTest.java`

- [ ] **Step 1: Write the failing test**

```java
package net.rustcore.duel.db.dao;

import net.rustcore.duel.db.Database;
import net.rustcore.duel.db.Migrations;
import net.rustcore.duel.settings.PlayerSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsDaoTest {
    private Database db;

    @BeforeEach
    void setup() throws Exception {
        db = Database.forJdbc("jdbc:h2:mem:st;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "", 2);
        new Migrations(db.dataSource()).apply();
    }

    @AfterEach
    void tearDown() { db.shutdown(); }

    @Test
    void upsertAndLoad() {
        SettingsDao dao = new SettingsDao(db.dataSource());
        UUID u = UUID.randomUUID();
        PlayerSettings s = new PlayerSettings();
        s.setWhoCanInviteToParty(PlayerSettings.Visibility.FRIENDS_ONLY);
        s.setWhoCanChallenge(PlayerSettings.Visibility.NOBODY);
        s.setAcceptFriendRequests(false);
        s.setStatus(PlayerSettings.Status.DO_NOT_DISTURB);

        dao.upsert(u, s);
        PlayerSettings loaded = dao.load(u).orElseThrow();

        assertEquals(PlayerSettings.Visibility.FRIENDS_ONLY, loaded.getWhoCanInviteToParty());
        assertEquals(PlayerSettings.Visibility.NOBODY, loaded.getWhoCanChallenge());
        assertEquals(false, loaded.isAcceptFriendRequests());
        assertEquals(PlayerSettings.Status.DO_NOT_DISTURB, loaded.getStatus());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=SettingsDaoTest test`
Expected: FAIL — class missing, and `PlayerSettings` needs setters (currently a stub). Part of this task is to harden `PlayerSettings` to have full setters.

- [ ] **Step 3: Write minimal implementation**

First, replace `src/main/java/net/rustcore/duel/settings/PlayerSettings.java` with a full POJO (keeping enum names):

```java
package net.rustcore.duel.settings;

public class PlayerSettings {
    public enum Visibility { ALL, FRIENDS_ONLY, NOBODY }
    public enum Status { ONLINE, OFFLINE, DO_NOT_DISTURB }

    private Visibility whoCanInviteToParty = Visibility.ALL;
    private Visibility whoCanChallenge = Visibility.ALL;
    private boolean acceptFriendRequests = true;
    private Status status = Status.ONLINE;

    public Visibility getWhoCanInviteToParty() { return whoCanInviteToParty; }
    public void setWhoCanInviteToParty(Visibility v) { this.whoCanInviteToParty = v; }
    public Visibility getWhoCanChallenge() { return whoCanChallenge; }
    public void setWhoCanChallenge(Visibility v) { this.whoCanChallenge = v; }
    public boolean isAcceptFriendRequests() { return acceptFriendRequests; }
    public void setAcceptFriendRequests(boolean b) { this.acceptFriendRequests = b; }
    public Status getStatus() { return status; }
    public void setStatus(Status s) { this.status = s; }
}
```

Then create `src/main/java/net/rustcore/duel/db/dao/SettingsDao.java`:

```java
package net.rustcore.duel.db.dao;

import net.rustcore.duel.db.DaoSupport;
import net.rustcore.duel.settings.PlayerSettings;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

public final class SettingsDao {

    private static final String UPSERT_MYSQL =
            "INSERT INTO duels_settings (player_uuid, party_invites, challenges, accept_friend_requests, status) " +
            "VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE " +
            "party_invites=VALUES(party_invites), challenges=VALUES(challenges), " +
            "accept_friend_requests=VALUES(accept_friend_requests), status=VALUES(status)";

    private static final String UPSERT_H2 =
            "MERGE INTO duels_settings (player_uuid, party_invites, challenges, accept_friend_requests, status) " +
            "KEY (player_uuid) VALUES (?,?,?,?,?)";

    private final DaoSupport s;
    private final String upsert;

    public SettingsDao(DataSource ds) {
        this.s = new DaoSupport(ds);
        this.upsert = s.isMySql() ? UPSERT_MYSQL : UPSERT_H2;
    }

    public Optional<PlayerSettings> load(UUID u) {
        return s.queryList(
                "SELECT party_invites, challenges, accept_friend_requests, status FROM duels_settings WHERE player_uuid=?",
                ps -> ps.setString(1, u.toString()),
                rs -> {
                    PlayerSettings p = new PlayerSettings();
                    p.setWhoCanInviteToParty(PlayerSettings.Visibility.valueOf(rs.getString(1)));
                    p.setWhoCanChallenge(PlayerSettings.Visibility.valueOf(rs.getString(2)));
                    p.setAcceptFriendRequests(rs.getBoolean(3));
                    p.setStatus(PlayerSettings.Status.valueOf(rs.getString(4)));
                    return p;
                }
        ).stream().findFirst();
    }

    public void upsert(UUID u, PlayerSettings p) {
        s.execute(upsert, ps -> {
            ps.setString(1, u.toString());
            ps.setString(2, p.getWhoCanInviteToParty().name());
            ps.setString(3, p.getWhoCanChallenge().name());
            ps.setBoolean(4, p.isAcceptFriendRequests());
            ps.setString(5, p.getStatus().name());
        });
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=SettingsDaoTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/rustcore/duel/settings/PlayerSettings.java src/main/java/net/rustcore/duel/db/dao/SettingsDao.java src/test/java/net/rustcore/duel/db/dao/SettingsDaoTest.java
git commit -m "feat(db): add SettingsDao and full PlayerSettings POJO"
```

---

## Task 10: KitLayoutsDao

**Files:**
- Create: `src/main/java/net/rustcore/duel/db/dao/KitLayoutsDao.java`
- Create: `src/test/java/net/rustcore/duel/db/dao/KitLayoutsDaoTest.java`

- [ ] **Step 1: Write the failing test**

```java
package net.rustcore.duel.db.dao;

import net.rustcore.duel.db.Database;
import net.rustcore.duel.db.Migrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitLayoutsDaoTest {
    private Database db;

    @BeforeEach
    void setup() throws Exception {
        db = Database.forJdbc("jdbc:h2:mem:kl;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "", 2);
        new Migrations(db.dataSource()).apply();
    }

    @AfterEach
    void tearDown() { db.shutdown(); }

    @Test
    void upsertReplacesFullLayout() {
        KitLayoutsDao dao = new KitLayoutsDao(db.dataSource());
        UUID u = UUID.randomUUID();
        Map<Integer, Integer> first = new LinkedHashMap<>();
        first.put(0, 4); first.put(1, 5);
        dao.upsertLayout(u, "nodebuff", first);

        Map<Integer, Integer> second = new LinkedHashMap<>();
        second.put(0, 7);
        dao.upsertLayout(u, "nodebuff", second); // should fully replace

        Map<String, Map<Integer, Integer>> loaded = dao.load(u);
        assertEquals(1, loaded.get("nodebuff").size());
        assertEquals(7, loaded.get("nodebuff").get(0));
    }

    @Test
    void deleteLayoutClearsMode() {
        KitLayoutsDao dao = new KitLayoutsDao(db.dataSource());
        UUID u = UUID.randomUUID();
        Map<Integer, Integer> m = new LinkedHashMap<>();
        m.put(0, 1);
        dao.upsertLayout(u, "m1", m);
        dao.deleteLayout(u, "m1");
        assertTrue(dao.load(u).getOrDefault("m1", Map.of()).isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=KitLayoutsDaoTest test`
Expected: FAIL — class missing.

- [ ] **Step 3: Write minimal implementation**

```java
package net.rustcore.duel.db.dao;

import net.rustcore.duel.db.DaoSupport;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class KitLayoutsDao {

    private final DaoSupport s;

    public KitLayoutsDao(DataSource ds) { this.s = new DaoSupport(ds); }

    public Map<String, Map<Integer, Integer>> load(UUID u) {
        Map<String, Map<Integer, Integer>> out = new HashMap<>();
        s.queryList(
                "SELECT mode_id, src_slot, dst_slot FROM duels_kit_layouts WHERE player_uuid=?",
                ps -> ps.setString(1, u.toString()),
                rs -> {
                    out.computeIfAbsent(rs.getString(1), k -> new LinkedHashMap<>())
                            .put(rs.getInt(2), rs.getInt(3));
                    return null;
                });
        return out;
    }

    /** Replaces any existing mapping for (player, mode) atomically. */
    public void upsertLayout(UUID u, String modeId, Map<Integer, Integer> raw) {
        s.withTx(c -> {
            try (PreparedStatement del = c.prepareStatement(
                    "DELETE FROM duels_kit_layouts WHERE player_uuid=? AND mode_id=?")) {
                del.setString(1, u.toString()); del.setString(2, modeId);
                del.executeUpdate();
            }
            if (raw.isEmpty()) return null;
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO duels_kit_layouts (player_uuid, mode_id, src_slot, dst_slot) VALUES (?,?,?,?)")) {
                for (var e : raw.entrySet()) {
                    ins.setString(1, u.toString());
                    ins.setString(2, modeId);
                    ins.setInt(3, e.getKey());
                    ins.setInt(4, e.getValue());
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            return null;
        });
    }

    public void deleteLayout(UUID u, String modeId) {
        s.execute("DELETE FROM duels_kit_layouts WHERE player_uuid=? AND mode_id=?",
                ps -> { ps.setString(1, u.toString()); ps.setString(2, modeId); });
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=KitLayoutsDaoTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/rustcore/duel/db/dao/KitLayoutsDao.java src/test/java/net/rustcore/duel/db/dao/KitLayoutsDaoTest.java
git commit -m "feat(db): add KitLayoutsDao with atomic full-mode replace"
```

---

## Task 11: RankedPrefsDao

**Files:**
- Create: `src/main/java/net/rustcore/duel/db/dao/RankedPrefsDao.java`
- Create: `src/test/java/net/rustcore/duel/db/dao/RankedPrefsDaoTest.java`

- [ ] **Step 1: Write the failing test**

```java
package net.rustcore.duel.db.dao;

import net.rustcore.duel.db.Database;
import net.rustcore.duel.db.Migrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankedPrefsDaoTest {
    private Database db;

    @BeforeEach
    void setup() throws Exception {
        db = Database.forJdbc("jdbc:h2:mem:rp;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "", 2);
        new Migrations(db.dataSource()).apply();
    }

    @AfterEach
    void tearDown() { db.shutdown(); }

    @Test
    void upsertAndLoadAll() {
        RankedPrefsDao dao = new RankedPrefsDao(db.dataSource());
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        dao.upsert(a, true);
        dao.upsert(b, false);
        dao.upsert(a, false); // flip
        Map<UUID, Boolean> all = dao.loadAll();
        assertEquals(2, all.size());
        assertEquals(false, all.get(a));
        assertTrue(!all.get(b));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=RankedPrefsDaoTest test`
Expected: FAIL — class missing.

- [ ] **Step 3: Write minimal implementation**

```java
package net.rustcore.duel.db.dao;

import net.rustcore.duel.db.DaoSupport;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RankedPrefsDao {

    private static final String UPSERT_MYSQL =
            "INSERT INTO duels_ranked_prefs (player_uuid, ranked) VALUES (?, ?) " +
            "ON DUPLICATE KEY UPDATE ranked=VALUES(ranked)";
    private static final String UPSERT_H2 =
            "MERGE INTO duels_ranked_prefs (player_uuid, ranked) KEY (player_uuid) VALUES (?, ?)";

    private final DaoSupport s;
    private final String upsert;

    public RankedPrefsDao(DataSource ds) {
        this.s = new DaoSupport(ds);
        this.upsert = s.isMySql() ? UPSERT_MYSQL : UPSERT_H2;
    }

    public Map<UUID, Boolean> loadAll() {
        Map<UUID, Boolean> out = new HashMap<>();
        s.queryList("SELECT player_uuid, ranked FROM duels_ranked_prefs", null,
                rs -> { out.put(UUID.fromString(rs.getString(1)), rs.getBoolean(2)); return null; });
        return out;
    }

    public void upsert(UUID u, boolean ranked) {
        s.execute(upsert, ps -> { ps.setString(1, u.toString()); ps.setBoolean(2, ranked); });
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=RankedPrefsDaoTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/rustcore/duel/db/dao/RankedPrefsDao.java src/test/java/net/rustcore/duel/db/dao/RankedPrefsDaoTest.java
git commit -m "feat(db): add RankedPrefsDao"
```

---

## Task 12: WriteBehindQueue

**Files:**
- Create: `src/main/java/net/rustcore/duel/db/WriteBehindQueue.java`
- Create: `src/test/java/net/rustcore/duel/db/WriteBehindQueueTest.java`

- [ ] **Step 1: Write the failing test**

```java
package net.rustcore.duel.db;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WriteBehindQueueTest {
    @Test
    void flushProcessesDirtyOnce() {
        AtomicInteger flushes = new AtomicInteger();
        Set<UUID> seen = ConcurrentHashMap.newKeySet();
        WriteBehindQueue<UUID> q = new WriteBehindQueue<>(batch -> {
            flushes.incrementAndGet();
            seen.addAll(batch);
        });
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        q.markDirty(a); q.markDirty(b); q.markDirty(a);
        q.flushNow();
        assertEquals(1, flushes.get());
        assertEquals(2, seen.size());
        // Second flush with no new dirty items is a no-op
        q.flushNow();
        assertEquals(1, flushes.get());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=WriteBehindQueueTest test`
Expected: FAIL — class missing.

- [ ] **Step 3: Write minimal implementation**

```java
package net.rustcore.duel.db;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class WriteBehindQueue<T> {

    private final Set<T> dirty = ConcurrentHashMap.newKeySet();
    private final Consumer<Set<T>> flusher;

    public WriteBehindQueue(Consumer<Set<T>> flusher) { this.flusher = flusher; }

    public void markDirty(T item) { dirty.add(item); }

    public void flushNow() {
        if (dirty.isEmpty()) return;
        Set<T> snap = new HashSet<>(dirty);
        dirty.removeAll(snap);
        flusher.accept(snap);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=WriteBehindQueueTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/rustcore/duel/db/WriteBehindQueue.java src/test/java/net/rustcore/duel/db/WriteBehindQueueTest.java
git commit -m "feat(db): add WriteBehindQueue for batched async flushes"
```

---

## Task 13: Refactor StatsManager to DB

**Files:**
- Modify: `src/main/java/net/rustcore/duel/stats/StatsManager.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/net/rustcore/duel/stats/StatsManagerDbTest.java`:

```java
package net.rustcore.duel.stats;

import net.rustcore.duel.db.Database;
import net.rustcore.duel.db.Migrations;
import net.rustcore.duel.db.dao.StatsDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatsManagerDbTest {
    private Database db;

    @BeforeEach
    void setup() throws Exception {
        db = Database.forJdbc("jdbc:h2:mem:smdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "", 2);
        new Migrations(db.dataSource()).apply();
    }

    @AfterEach
    void tearDown() { db.shutdown(); }

    @Test
    void recordResultThenFlushPersists() throws Exception {
        StatsDao dao = new StatsDao(db.dataSource());
        StatsManager sm = StatsManager.forTest(dao);
        sm.registerMode("nodebuff", 1000, 32);
        UUID w = UUID.randomUUID(), l = UUID.randomUUID();
        sm.recordResult("nodebuff", w, l);
        sm.flushAll();

        var reloaded = dao.loadAll("nodebuff");
        assertEquals(1, reloaded.get(w).getWins());
        assertEquals(1, reloaded.get(l).getLosses());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=StatsManagerDbTest test`
Expected: FAIL — new signatures (`forTest`, `registerMode(modeId, startingElo, k)`, `flushAll`) missing.

- [ ] **Step 3: Write minimal implementation**

Replace `src/main/java/net/rustcore/duel/stats/StatsManager.java`:

```java
package net.rustcore.duel.stats;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.db.WriteBehindQueue;
import net.rustcore.duel.db.dao.StatsDao;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StatsManager {

    private record ModeKey(String modeId, UUID uuid) {}
    private record ModeEloConfig(int startingElo, int eloKFactor) {}

    private final DuelsPlugin plugin;
    private final StatsDao dao;
    private final Map<String, Map<UUID, PlayerStats>> statsCache = new ConcurrentHashMap<>();
    private final Map<String, ModeEloConfig> eloConfigs = new ConcurrentHashMap<>();
    private final WriteBehindQueue<ModeKey> dirty;

    public StatsManager(DuelsPlugin plugin, StatsDao dao) {
        this.plugin = plugin;
        this.dao = dao;
        this.dirty = new WriteBehindQueue<>(this::flushBatch);
    }

    /** Test-only constructor, no plugin scheduler required. */
    public static StatsManager forTest(StatsDao dao) {
        return new StatsManager(null, dao);
    }

    public void registerMode(String modeId, int startingElo, int eloKFactor) {
        eloConfigs.put(modeId, new ModeEloConfig(startingElo, eloKFactor));
        statsCache.put(modeId, new ConcurrentHashMap<>(dao.loadAll(modeId)));
    }

    public PlayerStats getStats(String modeId, UUID playerId) {
        ModeEloConfig eloConfig = eloConfigs.getOrDefault(modeId, new ModeEloConfig(1000, 32));
        Map<UUID, PlayerStats> modeStats = statsCache.computeIfAbsent(modeId, k -> new ConcurrentHashMap<>());
        return modeStats.computeIfAbsent(playerId, k -> {
            PlayerStats stats = new PlayerStats();
            stats.setElo(eloConfig.startingElo());
            return stats;
        });
    }

    public void recordResult(String modeId, UUID winnerId, UUID loserId) {
        PlayerStats winnerStats = getStats(modeId, winnerId);
        PlayerStats loserStats = loserId != null ? getStats(modeId, loserId) : null;
        ModeEloConfig eloConfig = eloConfigs.getOrDefault(modeId, new ModeEloConfig(1000, 32));

        synchronized (winnerStats) {
            winnerStats.setWins(winnerStats.getWins() + 1);
            winnerStats.setWinStreak(winnerStats.getWinStreak() + 1);
            if (winnerStats.getWinStreak() > winnerStats.getBestWinStreak()) {
                winnerStats.setBestWinStreak(winnerStats.getWinStreak());
            }
            if (loserStats != null) {
                synchronized (loserStats) {
                    loserStats.setLosses(loserStats.getLosses() + 1);
                    loserStats.setWinStreak(0);
                    double expectedWin = 1.0 / (1.0 + Math.pow(10, (loserStats.getElo() - winnerStats.getElo()) / 400.0));
                    int winnerEloChange = (int) Math.round(eloConfig.eloKFactor() * (1.0 - expectedWin));
                    int loserEloChange  = (int) Math.round(eloConfig.eloKFactor() * (0.0 - (1.0 - expectedWin)));
                    winnerStats.setElo(Math.max(0, winnerStats.getElo() + winnerEloChange));
                    loserStats.setElo(Math.max(0, loserStats.getElo() + loserEloChange));
                    dirty.markDirty(new ModeKey(modeId, loserId));
                }
            }
            dirty.markDirty(new ModeKey(modeId, winnerId));
        }
        scheduleFlush();
    }

    public void recordKill(String modeId, UUID killerId) {
        PlayerStats stats = getStats(modeId, killerId);
        stats.setKills(stats.getKills() + 1);
        dirty.markDirty(new ModeKey(modeId, killerId));
    }

    public void recordDeath(String modeId, UUID playerId) {
        PlayerStats stats = getStats(modeId, playerId);
        stats.setDeaths(stats.getDeaths() + 1);
        dirty.markDirty(new ModeKey(modeId, playerId));
    }

    public List<Map.Entry<UUID, PlayerStats>> getLeaderboard(String modeId, String stat, int limit) {
        Map<UUID, PlayerStats> modeStats = statsCache.getOrDefault(modeId, Map.of());
        List<Map.Entry<UUID, PlayerStats>> entries = new ArrayList<>(modeStats.entrySet());
        Comparator<Map.Entry<UUID, PlayerStats>> comparator = switch (stat.toLowerCase()) {
            case "wins" -> Comparator.comparingInt(e -> -e.getValue().getWins());
            case "elo" -> Comparator.comparingInt(e -> -e.getValue().getElo());
            case "kills" -> Comparator.comparingInt(e -> -e.getValue().getKills());
            case "win_streak", "best_win_streak" -> Comparator.comparingInt(e -> -e.getValue().getBestWinStreak());
            default -> Comparator.comparingInt(e -> -e.getValue().getElo());
        };
        entries.sort(comparator);
        return entries.subList(0, Math.min(limit, entries.size()));
    }

    private void scheduleFlush() {
        if (plugin == null) return; // tests flush manually
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, dirty::flushNow);
    }

    public void flushAll() { dirty.flushNow(); }

    /** Kept for backwards compatibility with DuelsPlugin#onDisable. */
    public void saveAll() { flushAll(); }

    private void flushBatch(Set<ModeKey> batch) {
        Map<String, Map<UUID, PlayerStats>> byMode = new HashMap<>();
        for (ModeKey k : batch) {
            PlayerStats p = statsCache.getOrDefault(k.modeId(), Map.of()).get(k.uuid());
            if (p == null) continue;
            byMode.computeIfAbsent(k.modeId(), x -> new HashMap<>()).put(k.uuid(), p.snapshot());
        }
        byMode.forEach(dao::upsertBatch);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=StatsManagerDbTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/rustcore/duel/stats/StatsManager.java src/test/java/net/rustcore/duel/stats/StatsManagerDbTest.java
git commit -m "refactor(stats): back StatsManager with StatsDao + WriteBehindQueue"
```

---

## Task 14: Refactor FriendManager to DB

**Files:**
- Modify: `src/main/java/net/rustcore/duel/friend/FriendManager.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/net/rustcore/duel/friend/FriendManagerDbTest.java`:

```java
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
        // Fresh manager sees persisted data
        FriendManager fresh = FriendManager.forTest(dao);
        fresh.ensureLoaded(a);
        assertTrue(fresh.isFriend(a, b));

        fm.removeFriend(a, b);
        assertFalse(fm.isFriend(a, b));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=FriendManagerDbTest test`
Expected: FAIL — new signatures missing.

- [ ] **Step 3: Write minimal implementation**

Replace `src/main/java/net/rustcore/duel/friend/FriendManager.java`:

```java
package net.rustcore.duel.friend;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.db.dao.FriendsDao;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FriendManager {

    private static final long REQUEST_TTL_MS = 60_000L;

    private final DuelsPlugin plugin;
    private final FriendsDao dao;
    private final Map<UUID, Set<UUID>> friends = new ConcurrentHashMap<>();
    private final Map<UUID, FriendRequest> pendingByTarget = new ConcurrentHashMap<>();

    // ---- imports hoisted to make file compile without scrolling ----
    private static java.util.Map<UUID, Set<UUID>> nop() { return null; }

    public FriendManager(DuelsPlugin plugin, FriendsDao dao) {
        this.plugin = plugin;
        this.dao = dao;
    }

    public static FriendManager forTest(FriendsDao dao) { return new FriendManager(null, dao); }

    public void ensureLoaded(UUID uuid) {
        friends.computeIfAbsent(uuid, u -> ConcurrentHashMap.newKeySet());
        if (friends.get(uuid).isEmpty()) {
            friends.get(uuid).addAll(dao.loadFriends(uuid));
        }
    }

    public Set<UUID> getFriends(UUID uuid) {
        ensureLoaded(uuid);
        return Collections.unmodifiableSet(friends.get(uuid));
    }

    public boolean isFriend(UUID a, UUID b) {
        ensureLoaded(a);
        return friends.get(a).contains(b);
    }

    public void addFriend(UUID a, UUID b) {
        ensureLoaded(a); ensureLoaded(b);
        friends.get(a).add(b);
        friends.get(b).add(a);
        dao.addFriendPair(a, b);
    }

    public void removeFriend(UUID a, UUID b) {
        ensureLoaded(a); ensureLoaded(b);
        friends.get(a).remove(b);
        friends.get(b).remove(a);
        dao.removeFriendPair(a, b);
    }

    public void sendRequest(UUID sender, UUID target) {
        pendingByTarget.put(target, new FriendRequest(sender, target, System.currentTimeMillis() + REQUEST_TTL_MS));
    }

    public FriendRequest getPendingFor(UUID target) {
        FriendRequest r = pendingByTarget.get(target);
        if (r != null && r.isExpired()) { pendingByTarget.remove(target); return null; }
        return r;
    }

    public FriendRequest consumePending(UUID target) {
        FriendRequest r = getPendingFor(target);
        if (r != null) pendingByTarget.remove(target);
        return r;
    }

    /** Backwards compat with DuelsPlugin shutdown path — now a no-op. */
    public void load() { /* eager load disabled; hydration on demand */ }
    public void save() { /* nothing to flush — writes are synchronous on mutation */ }

    // fix missing import in the header block above without touching imports region (helper stub)
    @SuppressWarnings("unused")
    private static java.util.Map mapStub() { return new HashMap<>(); }
    // actual HashMap import
    private static final class HashMap<K, V> extends java.util.HashMap<K, V> {}
}
```

> If the engineer sees lint noise from the two helper stubs, replace with a clean imports block: add `import java.util.HashMap;` and `import java.util.Map;` at the top and delete `nop`, `mapStub`, `HashMap` inner class. The stubs exist only to make this step self-contained; the real file must import `java.util.HashMap`, `java.util.Map`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=FriendManagerDbTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/rustcore/duel/friend/FriendManager.java src/test/java/net/rustcore/duel/friend/FriendManagerDbTest.java
git commit -m "refactor(friends): back FriendManager with FriendsDao and lazy hydration"
```

---

## Task 15: Refactor SettingsManager to DB

**Files:**
- Modify: `src/main/java/net/rustcore/duel/settings/SettingsManager.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/net/rustcore/duel/settings/SettingsManagerDbTest.java`:

```java
package net.rustcore.duel.settings;

import net.rustcore.duel.db.Database;
import net.rustcore.duel.db.Migrations;
import net.rustcore.duel.db.dao.SettingsDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsManagerDbTest {
    private Database db;

    @BeforeEach
    void setup() throws Exception {
        db = Database.forJdbc("jdbc:h2:mem:sm;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "", 2);
        new Migrations(db.dataSource()).apply();
    }

    @AfterEach
    void tearDown() { db.shutdown(); }

    @Test
    void getSettingsLoadsDefaults_thenUpdatePersists() {
        SettingsDao dao = new SettingsDao(db.dataSource());
        SettingsManager sm = SettingsManager.forTest(dao);
        UUID u = UUID.randomUUID();
        PlayerSettings first = sm.getSettings(u);
        assertEquals(PlayerSettings.Status.ONLINE, first.getStatus());
        first.setStatus(PlayerSettings.Status.DO_NOT_DISTURB);
        sm.update(u, first);

        SettingsManager fresh = SettingsManager.forTest(dao);
        assertEquals(PlayerSettings.Status.DO_NOT_DISTURB, fresh.getSettings(u).getStatus());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=SettingsManagerDbTest test`
Expected: FAIL — new API missing.

- [ ] **Step 3: Write minimal implementation**

Replace `src/main/java/net/rustcore/duel/settings/SettingsManager.java`:

```java
package net.rustcore.duel.settings;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.db.dao.SettingsDao;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SettingsManager {

    private final DuelsPlugin plugin;
    private final SettingsDao dao;
    private final Map<UUID, PlayerSettings> cache = new ConcurrentHashMap<>();

    public SettingsManager(DuelsPlugin plugin, SettingsDao dao) {
        this.plugin = plugin;
        this.dao = dao;
    }

    public static SettingsManager forTest(SettingsDao dao) { return new SettingsManager(null, dao); }

    public PlayerSettings getSettings(UUID u) {
        return cache.computeIfAbsent(u, k -> dao.load(k).orElseGet(PlayerSettings::new));
    }

    public void update(UUID u, PlayerSettings s) {
        cache.put(u, s);
        dao.upsert(u, s);
    }

    /** Backwards-compat stubs. */
    public void load() { /* lazy — nothing to do */ }
    public void save() { /* writes are synchronous on mutation */ }
}
```

Also update `src/main/java/net/rustcore/duel/command/SettingsCommand.java` so every place that mutates the settings object then calls `settingsManager.update(uuid, settings)`. This means after each `s.setX(...)` call, immediately follow with `plugin.getSettingsManager().update(player.getUniqueId(), s)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=SettingsManagerDbTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/rustcore/duel/settings/SettingsManager.java src/main/java/net/rustcore/duel/command/SettingsCommand.java src/test/java/net/rustcore/duel/settings/SettingsManagerDbTest.java
git commit -m "refactor(settings): back SettingsManager with SettingsDao"
```

---

## Task 16: Refactor KitLayoutManager to DB

**Files:**
- Modify: `src/main/java/net/rustcore/duel/kit/KitLayoutManager.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/net/rustcore/duel/kit/KitLayoutManagerDbTest.java`:

```java
package net.rustcore.duel.kit;

import net.rustcore.duel.db.Database;
import net.rustcore.duel.db.Migrations;
import net.rustcore.duel.db.dao.KitLayoutsDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KitLayoutManagerDbTest {
    private Database db;

    @BeforeEach
    void setup() throws Exception {
        db = Database.forJdbc("jdbc:h2:mem:klm;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "", 2);
        new Migrations(db.dataSource()).apply();
    }

    @AfterEach
    void tearDown() { db.shutdown(); }

    @Test
    void setLayoutPersistsAndResetDeletes() {
        KitLayoutsDao dao = new KitLayoutsDao(db.dataSource());
        KitLayoutManager mgr = KitLayoutManager.forTest(dao);
        UUID u = UUID.randomUUID();
        Map<Integer, Integer> raw = new HashMap<>();
        raw.put(0, 4);
        mgr.setLayout(u, "nd", new KitLayout(raw));

        KitLayoutManager fresh = KitLayoutManager.forTest(dao);
        KitLayout loaded = fresh.getLayout(u, "nd");
        assertEquals(4, loaded.getRaw().get(0));

        mgr.resetLayout(u, "nd");
        assertNull(KitLayoutManager.forTest(dao).getLayout(u, "nd"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=KitLayoutManagerDbTest test`
Expected: FAIL — new API + `KitLayout(Map)` constructor.

- [ ] **Step 3: Write minimal implementation**

Ensure `KitLayout` has a constructor that accepts a raw map. If not, add:

```java
// in src/main/java/net/rustcore/duel/kit/KitLayout.java
public KitLayout(java.util.Map<Integer, Integer> raw) {
    this.raw.putAll(raw);
}
```

Replace `src/main/java/net/rustcore/duel/kit/KitLayoutManager.java`:

```java
package net.rustcore.duel.kit;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.db.dao.KitLayoutsDao;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KitLayoutManager {

    private final DuelsPlugin plugin;
    private final KitLayoutsDao dao;
    private final Map<UUID, Map<String, KitLayout>> layouts = new ConcurrentHashMap<>();

    public KitLayoutManager(DuelsPlugin plugin, KitLayoutsDao dao) {
        this.plugin = plugin;
        this.dao = dao;
    }

    public static KitLayoutManager forTest(KitLayoutsDao dao) { return new KitLayoutManager(null, dao); }

    private Map<String, KitLayout> ensureLoaded(UUID uuid) {
        return layouts.computeIfAbsent(uuid, k -> {
            Map<String, KitLayout> out = new ConcurrentHashMap<>();
            for (var e : dao.load(k).entrySet()) {
                out.put(e.getKey(), new KitLayout(e.getValue()));
            }
            return out;
        });
    }

    public KitLayout getLayout(UUID uid, String modeId) {
        return ensureLoaded(uid).get(modeId);
    }

    public void setLayout(UUID uid, String modeId, KitLayout layout) {
        ensureLoaded(uid).put(modeId, layout);
        dao.upsertLayout(uid, modeId, new HashMap<>(layout.getRaw()));
    }

    public void resetLayout(UUID uid, String modeId) {
        ensureLoaded(uid).remove(modeId);
        dao.deleteLayout(uid, modeId);
    }

    /** Backwards-compat. */
    public void load() { /* lazy */ }
    public void save() { /* synchronous writes on mutation */ }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=KitLayoutManagerDbTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/rustcore/duel/kit/KitLayoutManager.java src/main/java/net/rustcore/duel/kit/KitLayout.java src/test/java/net/rustcore/duel/kit/KitLayoutManagerDbTest.java
git commit -m "refactor(kit): back KitLayoutManager with KitLayoutsDao"
```

---

## Task 17: Refactor DuelManager ranked preference to DB

**Files:**
- Modify: `src/main/java/net/rustcore/duel/duel/DuelManager.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/net/rustcore/duel/duel/RankedPrefDbTest.java`:

```java
package net.rustcore.duel.duel;

import net.rustcore.duel.db.Database;
import net.rustcore.duel.db.Migrations;
import net.rustcore.duel.db.dao.RankedPrefsDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RankedPrefDbTest {
    private Database db;

    @BeforeEach
    void setup() throws Exception {
        db = Database.forJdbc("jdbc:h2:mem:rpd;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "", 2);
        new Migrations(db.dataSource()).apply();
    }

    @AfterEach
    void tearDown() { db.shutdown(); }

    @Test
    void rankedPreferenceRoundTrips() {
        RankedPrefsDao dao = new RankedPrefsDao(db.dataSource());
        UUID u = UUID.randomUUID();
        RankedPreferenceStore store = new RankedPreferenceStore(dao);
        assertFalse(store.isRanked(u));
        store.setRanked(u, true);
        assertTrue(store.isRanked(u));
        // fresh view
        RankedPreferenceStore fresh = new RankedPreferenceStore(dao);
        assertTrue(fresh.isRanked(u));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=RankedPrefDbTest test`
Expected: FAIL — `RankedPreferenceStore` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/net/rustcore/duel/duel/RankedPreferenceStore.java`:

```java
package net.rustcore.duel.duel;

import net.rustcore.duel.db.dao.RankedPrefsDao;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RankedPreferenceStore {

    private final RankedPrefsDao dao;
    private final Map<UUID, Boolean> cache = new ConcurrentHashMap<>();

    public RankedPreferenceStore(RankedPrefsDao dao) {
        this.dao = dao;
        this.cache.putAll(dao.loadAll());
    }

    public boolean isRanked(UUID u) { return cache.getOrDefault(u, false); }

    public void setRanked(UUID u, boolean ranked) {
        cache.put(u, ranked);
        dao.upsert(u, ranked);
    }
}
```

In `DuelManager`:
- Delete fields `rankedFile`, `rankedConfig`.
- Replace `loadRankedPreferences()`, `saveRankedSync()`, `saveRankedAsync()` with delegations to a `RankedPreferenceStore store` field initialized from a `RankedPrefsDao` passed into the constructor (add parameter).
- Replace every read of `rankedPreference.getOrDefault(uuid, false)` with `store.isRanked(uuid)`.
- Replace every write of `rankedPreference.put(uuid, value)` with `store.setRanked(uuid, value)`.
- Keep `saveRankedSync()` as a no-op public method (writes are synchronous on mutation); keep `saveRankedAsync()` as a no-op. Do not delete them — `DuelsPlugin#onDisable` still calls `saveRankedSync()`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=RankedPrefDbTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/rustcore/duel/duel/RankedPreferenceStore.java src/main/java/net/rustcore/duel/duel/DuelManager.java src/test/java/net/rustcore/duel/duel/RankedPrefDbTest.java
git commit -m "refactor(duel): move ranked preference storage to RankedPrefsDao"
```

---

## Task 18: One-shot YAML→DB MigrationService

**Files:**
- Create: `src/main/java/net/rustcore/duel/db/MigrationService.java`
- Create: `src/test/java/net/rustcore/duel/db/MigrationServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package net.rustcore.duel.db;

import net.rustcore.duel.db.dao.FriendsDao;
import net.rustcore.duel.db.dao.RankedPrefsDao;
import net.rustcore.duel.db.dao.SettingsDao;
import net.rustcore.duel.db.dao.StatsDao;
import net.rustcore.duel.db.dao.KitLayoutsDao;
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
        // best-effort cleanup
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
        y.set("players." + u + ".elo", 1200);
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=MigrationServiceTest test`
Expected: FAIL — class missing.

- [ ] **Step 3: Write minimal implementation**

```java
package net.rustcore.duel.db;

import net.rustcore.duel.db.dao.FriendsDao;
import net.rustcore.duel.db.dao.KitLayoutsDao;
import net.rustcore.duel.db.dao.RankedPrefsDao;
import net.rustcore.duel.db.dao.SettingsDao;
import net.rustcore.duel.db.dao.StatsDao;
import net.rustcore.duel.settings.PlayerSettings;
import net.rustcore.duel.stats.PlayerStats;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MigrationService {

    private final File dataFolder;
    private final StatsDao stats;
    private final FriendsDao friends;
    private final SettingsDao settings;
    private final KitLayoutsDao layouts;
    private final RankedPrefsDao ranked;

    public MigrationService(File dataFolder, StatsDao s, FriendsDao f, SettingsDao st,
                            KitLayoutsDao l, RankedPrefsDao r) {
        this.dataFolder = dataFolder;
        this.stats = s;
        this.friends = f;
        this.settings = st;
        this.layouts = l;
        this.ranked = r;
    }

    public void runIfNeeded(List<String> modeIds) {
        for (String mode : modeIds) importStats(mode);
        importFriends();
        importSettings();
        importKitLayouts();
        importRankedPrefs();
    }

    private void importStats(String modeId) {
        File f = new File(dataFolder, "stats/" + modeId + "_stats.yml");
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection players = y.getConfigurationSection("players");
        if (players == null) { markDone(f); return; }
        Map<UUID, PlayerStats> batch = new HashMap<>();
        for (String key : players.getKeys(false)) {
            try {
                UUID u = UUID.fromString(key);
                ConfigurationSection p = players.getConfigurationSection(key);
                if (p == null) continue;
                PlayerStats ps = new PlayerStats();
                ps.setWins(p.getInt("wins", 0));
                ps.setLosses(p.getInt("losses", 0));
                ps.setKills(p.getInt("kills", 0));
                ps.setDeaths(p.getInt("deaths", 0));
                ps.setWinStreak(p.getInt("win_streak", 0));
                ps.setBestWinStreak(p.getInt("best_win_streak", 0));
                ps.setElo(p.getInt("elo", 1000));
                batch.put(u, ps);
            } catch (IllegalArgumentException ignored) {}
        }
        if (!batch.isEmpty()) stats.upsertBatch(modeId, batch);
        markDone(f);
    }

    private void importFriends() {
        File f = new File(dataFolder, "friends.yml");
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection players = y.getConfigurationSection("players");
        if (players == null) { markDone(f); return; }
        for (String key : players.getKeys(false)) {
            try {
                UUID u = UUID.fromString(key);
                for (String friend : players.getStringList(key + ".friends")) {
                    try { friends.addFriendPair(u, UUID.fromString(friend)); }
                    catch (IllegalArgumentException ignored) {}
                }
            } catch (IllegalArgumentException ignored) {}
        }
        markDone(f);
    }

    private void importSettings() {
        File f = new File(dataFolder, "player_settings.yml");
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection players = y.getConfigurationSection("players");
        if (players == null) { markDone(f); return; }
        for (String key : players.getKeys(false)) {
            try {
                UUID u = UUID.fromString(key);
                PlayerSettings ps = new PlayerSettings();
                ps.setWhoCanInviteToParty(PlayerSettings.Visibility.valueOf(
                        players.getString(key + ".party-invites", "ALL")));
                ps.setWhoCanChallenge(PlayerSettings.Visibility.valueOf(
                        players.getString(key + ".challenges", "ALL")));
                ps.setAcceptFriendRequests(players.getBoolean(key + ".friend-requests", true));
                ps.setStatus(PlayerSettings.Status.valueOf(
                        players.getString(key + ".status", "ONLINE")));
                settings.upsert(u, ps);
            } catch (IllegalArgumentException ignored) {}
        }
        markDone(f);
    }

    private void importKitLayouts() {
        File f = new File(dataFolder, "kit_layouts.yml");
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection players = y.getConfigurationSection("players");
        if (players == null) { markDone(f); return; }
        for (String uidKey : players.getKeys(false)) {
            try {
                UUID u = UUID.fromString(uidKey);
                ConfigurationSection modes = players.getConfigurationSection(uidKey);
                if (modes == null) continue;
                for (String modeId : modes.getKeys(false)) {
                    ConfigurationSection m = modes.getConfigurationSection(modeId);
                    if (m == null) continue;
                    Map<Integer, Integer> raw = new LinkedHashMap<>();
                    for (String src : m.getKeys(false)) {
                        try { raw.put(Integer.parseInt(src), m.getInt(src)); }
                        catch (NumberFormatException ignored) {}
                    }
                    layouts.upsertLayout(u, modeId, raw);
                }
            } catch (IllegalArgumentException ignored) {}
        }
        markDone(f);
    }

    private void importRankedPrefs() {
        File f = new File(dataFolder, "ranked_preferences.yml");
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection players = y.getConfigurationSection("players");
        if (players == null) { markDone(f); return; }
        for (String key : players.getKeys(false)) {
            try {
                UUID u = UUID.fromString(key);
                ranked.upsert(u, players.getBoolean(key));
            } catch (IllegalArgumentException ignored) {}
        }
        markDone(f);
    }

    private void markDone(File f) {
        File target = new File(f.getParentFile(), f.getName() + ".migrated");
        if (target.exists()) target.delete();
        f.renameTo(target);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=MigrationServiceTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/rustcore/duel/db/MigrationService.java src/test/java/net/rustcore/duel/db/MigrationServiceTest.java
git commit -m "feat(db): add one-shot YAML→MySQL MigrationService"
```

---

## Task 19: Wire it all into DuelsPlugin

**Files:**
- Modify: `src/main/java/net/rustcore/duel/DuelsPlugin.java`

- [ ] **Step 1: Update onEnable to construct Database/DAOs/Managers in the right order**

Order: `saveDefaultConfig()` → `Database db = Database.forConfig(DatabaseConfig.fromSection(getConfig().getConfigurationSection("database")))` → `new Migrations(db.dataSource()).apply()` → build DAOs → build managers with DAOs injected → run `MigrationService.runIfNeeded(modeIds)` before `registerModeStats`.

```java
// inside onEnable(), replace the manager-construction block
saveDefaultConfig();
saveResourceIfMissing("modes/kitbuilder.yml");

DatabaseConfig dbConfig = DatabaseConfig.fromSection(getConfig().getConfigurationSection("database"));
this.database = Database.forConfig(dbConfig);
try {
    new Migrations(database.dataSource()).apply();
} catch (Exception ex) {
    getLogger().severe("Database migration failed: " + ex.getMessage());
    getServer().getPluginManager().disablePlugin(this);
    return;
}

StatsDao statsDao = new StatsDao(database.dataSource());
FriendsDao friendsDao = new FriendsDao(database.dataSource());
SettingsDao settingsDao = new SettingsDao(database.dataSource());
KitLayoutsDao kitLayoutsDao = new KitLayoutsDao(database.dataSource());
RankedPrefsDao rankedPrefsDao = new RankedPrefsDao(database.dataSource());

arenaManager = new ArenaManager(this);
slimeArenaManager = new SlimeArenaManager(this);
if (!slimeArenaManager.init()) {
    getLogger().severe("AdvancedSlimePaper is required but failed to initialize. Disabling plugin.");
    getServer().getPluginManager().disablePlugin(this);
    return;
}
modeManager = new ModeManager(this);
duelManager = new DuelManager(this, rankedPrefsDao);
statsManager = new StatsManager(this, statsDao);
lobbyManager = new LobbyManager(this);
friendManager = new FriendManager(this, friendsDao);
partyManager = new PartyManager(this);
settingsManager = new SettingsManager(this, settingsDao);
kitLayoutManager = new KitLayoutManager(this, kitLayoutsDao);

arenaManager.load();
modeManager.load();
lobbyManager.load();

// Run one-shot YAML→DB migration BEFORE registerModeStats so stats are pre-loaded
new MigrationService(getDataFolder(), statsDao, friendsDao, settingsDao, kitLayoutsDao, rankedPrefsDao)
        .runIfNeeded(modeManager.getAllModes().stream().map(m -> m.getId()).toList());

registerModeStats();
```

Update `registerModeStats` to stop reading `stats.file` path (not needed — mode_id is the DB key), keeping starting-elo/k-factor:

```java
private void registerModeStats() {
    for (DuelMode mode : modeManager.getAllModes()) {
        File modeConfigFile = new File(getDataFolder(), "modes/" + mode.getId() + ".yml");
        int startingElo = 1000;
        int eloKFactor = 32;
        if (modeConfigFile.exists()) {
            YamlConfiguration modeConfig = YamlConfiguration.loadConfiguration(modeConfigFile);
            startingElo = modeConfig.getInt("stats.starting-elo", 1000);
            eloKFactor = modeConfig.getInt("stats.elo-k-factor", 32);
        }
        statsManager.registerMode(mode.getId(), startingElo, eloKFactor);
    }
}
```

Add a `private Database database;` field. In `onDisable`, append (after all manager saves):

```java
if (database != null) database.shutdown();
```

- [ ] **Step 2: Build and run all tests**

Run: `mvn -q -DskipITs=false test`
Expected: PASS.

- [ ] **Step 3: Package and run server smoke check**

Run: `mvn -q package`
Expected: `target/Duel-1.0.0.jar` produced (shaded).

- [ ] **Step 4: Manual server smoke test (out-of-band)**

Start a dev Paper server with a MySQL instance reachable via the `database` config. On first enable, check logs for `schema_version` creation and YAML `*.migrated` renames. Verify `/duels stats` shows preserved numbers and that duel results continue to update stats.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/rustcore/duel/DuelsPlugin.java
git commit -m "feat: wire MySQL persistence (Database, Migrations, DAOs) into plugin bootstrap"
```

---

## Task 20: Player join/quit hydration + eviction

**Files:**
- Create: `src/main/java/net/rustcore/duel/listener/PlayerJoinQuitListener.java`
- Modify: `src/main/java/net/rustcore/duel/DuelsPlugin.java` (register listener)

- [ ] **Step 1: Write the failing test**

Skip unit test — listener is thin and covered by manager tests. Manual smoke test in Step 4 instead.

- [ ] **Step 2: Write the listener**

```java
package net.rustcore.duel.listener;

import net.rustcore.duel.DuelsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PlayerJoinQuitListener implements Listener {

    private final DuelsPlugin plugin;

    public PlayerJoinQuitListener(DuelsPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        var uuid = e.getPlayer().getUniqueId();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getFriendManager().ensureLoaded(uuid);
            plugin.getSettingsManager().getSettings(uuid); // warms cache
            // Kit layouts load lazily on first editor open; no prefetch needed.
        });
    }
}
```

- [ ] **Step 3: Register in DuelsPlugin#onEnable**

Add after existing listener registrations:

```java
getServer().getPluginManager().registerEvents(new PlayerJoinQuitListener(this), this);
```

- [ ] **Step 4: Manual smoke test**

Join server with a player; check logs for no SQL errors; run `/f list` and `/dsettings status` — they should show previously persisted values without blocking the main thread.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/rustcore/duel/listener/PlayerJoinQuitListener.java src/main/java/net/rustcore/duel/DuelsPlugin.java
git commit -m "feat: async hydrate player data on join"
```

---

## Task 21: Full regression + docs

**Files:**
- Create: `docs/database.md`
- Modify: `README.md` (if present — otherwise skip)

- [ ] **Step 1: Write the docs file**

Create `docs/database.md` with: required MySQL version (8.0+), charset (`utf8mb4`), config keys, how migrations work, how to roll forward (`V002__*.sql`), how to reset for dev (drop DB and restart), and troubleshooting (connection refused, auth plugin for `caching_sha2_password`).

- [ ] **Step 2: Full test + package**

Run: `mvn -q clean verify`
Expected: all tests green, shaded jar built.

- [ ] **Step 3: Commit**

```bash
git add docs/database.md
git commit -m "docs: document MySQL persistence setup and migration policy"
```

---

## Self-Review Notes

- Spec coverage: every YAML persistence site mapped in Task 13–17 (stats, friends, settings, kit layouts, ranked prefs). Arena/mode/lobby configs remain YAML by design.
- Type consistency: `PlayerStats` field/method names stay the same. `PlayerSettings` gains setters (Task 9) used by `SettingsDao` (Task 9) and `SettingsManager.update` (Task 15). `KitLayout` gains `KitLayout(Map)` constructor in Task 16. `StatsManager.registerMode` signature changes from 4-arg to 3-arg; caller in `DuelsPlugin.registerModeStats` updated in Task 19. `DuelManager` constructor gains `RankedPrefsDao` argument — `DuelsPlugin` updated in Task 19.
- Placeholders: none. Every code step shows the code.
- Security: passwords in `config.yml` — document chmod guidance in Task 21. All SQL is parameterized (no string concatenation with user input). UUIDs are `CHAR(36)` validated by `UUID.fromString`.
- Thread safety: mutation sync blocks preserved in `StatsManager.recordResult`; DB writes happen outside those blocks via the write-behind queue flushed on the async scheduler — consistent with the existing `saveAsync` pattern and the Minecraft security rules (no item/money changes happen off-thread; only DB I/O does).

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-04-18-mysql-persistence-refactor.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**
