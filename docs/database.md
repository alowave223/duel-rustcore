# MySQL Persistence

All player-scoped state (stats, friends, settings, kit layouts, ranked preferences) lives in MySQL. YAML files are no longer read after the one-shot migration runs.

## Requirements

- **MySQL 8.0+** (tested against 8.0 and 8.4).
- **Charset:** `utf8mb4`, collation `utf8mb4_0900_ai_ci` (default in 8.x).
- **Auth plugin:** the shaded driver (`mysql-connector-j 8.4.0`) supports `caching_sha2_password` by default. If connecting to an older server forcing `mysql_native_password`, either upgrade the server or create a dedicated user with `CREATE USER ... IDENTIFIED WITH mysql_native_password BY '...'`.
- **Java 21** (plugin build target).

## Config

`plugins/Duels/config.yml`:

```yaml
database:
  jdbc-url: "jdbc:mysql://127.0.0.1:3306/duels?useSSL=false&serverTimezone=UTC&rewriteBatchedStatements=true"
  user: "duels"
  password: "change-me"
  max-pool-size: 10
```

For development/CI, point at H2 in MySQL-compat mode:

```yaml
database:
  jdbc-url: "jdbc:h2:file:./plugins/Duels/data/duels;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
  user: "sa"
  password: ""
  max-pool-size: 4
```

Connection pool is HikariCP (shaded to `net.rustcore.duel.lib.hikari`). No external Hikari on the server required.

## Schema & migrations

Migrations live in `src/main/resources/db/migrations/V###__name.sql` and are applied by `Migrations.apply()` on plugin enable. State is tracked in table `schema_version(id INT PK, applied_at TIMESTAMP)`.

Current files:

| Version | Adds                                                       |
|---------|------------------------------------------------------------|
| V001    | `duels_stats`, `duels_friends`, `duels_settings`, `duels_kit_layouts`, `duels_ranked_prefs`, `schema_version` |

### Adding a migration

1. Create `src/main/resources/db/migrations/V00N__what_it_does.sql`.
2. Files must be strictly increasing; gaps are tolerated but discouraged.
3. Statements separated by `;` — `Migrations` splits on naked semicolons outside quotes.
4. Migrations are applied inside a transaction per file; a failure rolls back and the plugin refuses to enable.
5. **Never edit a committed `V###__` file.** Add `V###+1` instead. Tests fail if a previously applied checksum changes.

### Rolling forward

Deploy the new jar. On next server boot the unseen `V###` files apply in order. No manual steps.

### Resetting for dev

```sql
DROP DATABASE duels;
CREATE DATABASE duels CHARACTER SET utf8mb4;
```

Restart the server — `V001` reapplies on a blank database.

## One-shot YAML → MySQL migration

On first boot after upgrading, `MigrationService.runIfNeeded(modeIds)` imports:

| YAML file                               | Target table         |
|-----------------------------------------|----------------------|
| `data/stats/<modeId>_stats.yml`         | `duels_stats`        |
| `data/friends.yml`                      | `duels_friends`      |
| `data/player_settings.yml`              | `duels_settings`     |
| `data/kit_layouts.yml`                  | `duels_kit_layouts`  |
| `data/ranked_preferences.yml`           | `duels_ranked_prefs` |

After import each file is renamed to `*.yml.migrated`. The service is idempotent — if the `.migrated` marker is present the file is skipped on subsequent boots.

## Troubleshooting

| Symptom                                                                 | Fix |
|--------------------------------------------------------------------------|-----|
| `Communications link failure` / `Connection refused`                    | Check host/port; verify `bind-address` and firewall; confirm `skip-networking` is disabled. |
| `Access denied for user 'duels'@'...'`                                  | `GRANT ALL ON duels.* TO 'duels'@'%'; FLUSH PRIVILEGES;` |
| `Unable to load authentication plugin 'caching_sha2_password'`          | Upgrade driver (already 8.4.0 here) or recreate user with `mysql_native_password`. |
| `Duplicate entry '...' for key 'PRIMARY'` during YAML migration         | The `.migrated` rename failed previously. Manually rename the offending `*.yml` and retry. |
| `schema_version` table stuck at N but new file is N+1 and not applied    | File is not on the classpath of the shaded jar — rebuild with `mvn clean package`. |
| Pool exhaustion (`HikariPool ... Connection is not available`)          | Raise `database.max-pool-size`; check for long-running transactions in custom SQL. |

## Operational notes

- All writes are synchronous on the calling thread. Hot paths (stats) are buffered through `WriteBehindQueue` + a periodic flusher driven by `StatsManager`.
- `Database.shutdown()` runs from `onDisable`; it drains in-flight flushes before closing the Hikari pool.
- For cross-server deployments sharing one MySQL instance, each server writes using its own pool; last-write-wins on the row granularity. No cross-server locking is performed — design your modes around that.
