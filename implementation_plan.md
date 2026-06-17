# Implementation Plan

[Overview]
Apply 14 fixes (2 critical, 4 high, 8 medium/low) to the duel-rustcore Minecraft plugin using strict TDD methodology per `.clinerules/` guidelines — tests first, each fix preceded by a failing test that reproduces the bug.

This plan addresses security vulnerabilities identified per `.clinerules/minecraft-security.md` (anti-dupe, inventory safety, thread safety), code quality issues per `.clinerules/ponytail.md` (YAGNI, deduplication), and guardrail violations per `.clinerules/everything-claude-code-guardrails.md`. The plan follows the `tdd-workflow` skill pattern: RED (failing test) → GREEN (minimal fix) → refactor commit per fix category. The existing test infrastructure uses JUnit 5 + H2 in-memory database; new tests will follow the `DaoSupportTest` / `DatabaseTest` patterns already established in `src/test/java/net/rustcore/duel/`.

[Types]
No new types are introduced. Existing types are modified.

**Modification record** — `FixedKitMode` will return `Modification.DEFAULTS` instead of `null`.

**KitMenu** — add `ConcurrentHashMap<UUID, Long> lastClickTimestamps` field for double-click debounce.

**PlayerStats** — already has `synchronized snapshot()` (safe for async use), no changes needed.

[Files]

New files to be created:
- `src/test/java/net/rustcore/duel/mode/FixedKitModeTest.java` — verifies getModification() returns non-null DEFAULTS
- `src/test/java/net/rustcore/duel/stats/StatsManagerConcurrencyTest.java` — verifies concurrent recordResult + flush produces correct database values
- `src/test/java/net/rustcore/duel/kit/KitMenuDupeTest.java` — verifies double-click within 50ms fails to grant extra items
- `src/test/java/net/rustcore/duel/db/DaoSupportLazyTest.java` — verifies DaoSupport works when DB is temporarily unavailable
- `src/test/java/net/rustcore/duel/db/DatabaseConfigValidationTest.java` — verifies empty password rejects
- `src/test/java/net/rustcore/duel/duel/DuelManagerRaceTest.java` — verifies simultaneous queue entries handled atomically

Existing files to be modified:
- `src/main/java/net/rustcore/duel/mode/impl/FixedKitMode.java` — return Modification.DEFAULTS instead of null
- `src/main/java/net/rustcore/duel/stats/StatsManager.java` — use PlayerStats.snapshot() inside sync block for async flush
- `src/main/java/net/rustcore/duel/kit/KitMenu.java` — add 50ms click debounce per player
- `src/main/java/net/rustcore/duel/listener/PlayerJoinQuitListener.java` — remove runTaskAsynchronously
- `src/main/java/net/rustcore/duel/listener/DuelListener.java` — cancel InventoryClickEvent for all non-DRAFTING states before logic
- `src/main/java/net/rustcore/duel/duel/DuelManager.java` — use putIfAbsent for atomic player registration
- `src/main/java/net/rustcore/duel/command/DuelCommand.java` — extract validateBestOf() method, deduplicate ~180 lines
- `src/main/java/net/rustcore/duel/duel/Duel.java` — extract recordMatchResult() and cleanupAndReturnToLobby() methods
- `src/main/java/net/rustcore/duel/db/DaoSupport.java` — make isMySql computation lazy (compute on first use instead of constructor)
- `src/main/java/net/rustcore/duel/db/DatabaseConfig.java` — throw on empty password instead of silently defaulting to ""
- `src/main/java/net/rustcore/duel/mode/DuelMode.java` — add @NotNull annotation to getModification() (documentation)
- `src/main/java/net/rustcore/duel/rating/RatingClient.java` — remove secret_fp log line

Files NOT modified:
- `.clinerules/` — no changes
- `CLAUDE.md` — no changes
- `pom.xml` — no dependency changes
- `src/main/resources/config.yml` — no config changes (dead config removal is out of scope)
- `rating-service/` — not part of this plan

[Functions]

New functions:
- `DuelCommand.validateBestOf(CommandSender sender, String modeId, String input)` — input validation shared by queue/challenge/forcestart
- `Duel.recordMatchResult(String modeId, UUID winnerId)` — rating submission deduplication
- `Duel.cleanupAndReturnToLobby()` — teleport + pearl cleanup + arena deallocation shared by endDuel and forceEnd

Modified functions:
- `FixedKitMode.getModification()` → return Modification.DEFAULTS (was null)
- `StatsManager.scheduleFlush()` → capture snapshot synchronously before async dispatch
- `KitMenu.handleClick(Player player, int slot, Inventory clickedInventory)` → add 50ms per-player debounce
- `PlayerJoinQuitListener.onJoin(PlayerJoinEvent e)` → remove runTaskAsynchronously wrapper
- `DuelListener.onInventoryClick(InventoryClickEvent event)` → inverted logic: cancel first for non-DRAFTING, then bypass for KitBuilderMode
- `DuelManager.createDuel(String modeId, List<Player> players, int bestOf, boolean rankedMatch)` → use putIfAbsent for playerDuels
- `DaoSupport.computeIsMySql(DataSource ds)` → make field `volatile` + lazy initialization in getter
- `DatabaseConfig.fromSection(ConfigurationSection s)` → validate password non-empty
- `RatingClient.rate(RatingRequest.Body body)` → remove secret_fp log line, keep secret_len only

Removed functions: None.

[Classes]

No new classes; no removed classes.

Modified classes:
- `FixedKitMode` — one-line fix (getModification returns DEFAULTS)
- `StatsManager` — snapshot capture before async dispatch
- `KitMenu` — debounce field + handleClick logic
- `PlayerJoinQuitListener` — remove async wrapper
- `DuelListener` — inventory click logic inversion
- `DuelManager` — putIfAbsent atomicity
- `DuelCommand` — extract validateBestOf, deduplicate
- `Duel` — extract two helper methods
- `DaoSupport` — lazy isMySql computation
- `DatabaseConfig` — empty password validation
- `DuelMode` — annotation only
- `RatingClient` — remove log line

[Dependencies]
No new dependencies. Existing dependencies unchanged. H2 (test scope) and JUnit Jupiter 5.11.3 verify coverage. Maven Surefire 3.2.5 runs tests. No version bumps required.

[Testing]

All tests follow the existing JUnit 5 pattern (package-private test classes, descriptive method names). Each test file targets a specific bug/fix:

**Test 1: `FixedKitModeTest.java`**
- `getModification_neverReturnsNull()` — creates FixedKitMode from a minimal config section; asserts result is non-null and equals Modification.DEFAULTS

**Test 2: `StatsManagerConcurrencyTest.java`**
- `concurrentRecordAndFlush_producesCorrectValues()` — uses same thread-safety pattern as existing WriteBehindQueueTest: creates StatsManager with in-memory DAO, records 100 results in rapid succession, flushes, verifies DB has correct counts

**Test 3: `KitMenuDupeTest.java`**
- `rapidDoubleClick_onlyGrantsOneItem()` — verifies that calling handleClick with same slot twice within 20ms only results in one inventory addition

**Test 4: `DaoSupportLazyTest.java`**
- `daoSupport_worksWhenDbTemporarilyUnavailable_thenAvailable()` — verifies Daosupport constructed without immediate connection works once connection is available

**Test 5: `DatabaseConfigValidationTest.java`**
- `fromSection_withEmptyPassword_throws()` — verifies that config section with empty password string throws IllegalStateException

**Test 6: `DuelManagerRaceTest.java`**
- `simultaneousQueueJoins_produceSingleMatch()` — verifies two players joining same queue simultaneously match exactly once

**Regression tests** (must remain green):
- All 15 existing test files under `src/test/java/net/rustcore/duel/` must continue passing
- Existing `StatsManagerDbTest`, `SettingsManagerDbTest`, `FriendManagerDbTest`, `DuelManagerRankedMatchTest`, etc.

[Implementation Order]

The fixes are ordered by dependency (no fix depends on another fix being complete) and by TDD cycle efficiency. Each step compiles and runs independently.

Step 1: Fix #1 — FixedKitMode NPE (Critical)
  - Write `FixedKitModeTest.java` → RED
  - Fix `FixedKitMode.getModification()` → GREEN
  - Git commit: `fix: FixedKitMode returns Modification.DEFAULTS instead of null`

Step 2: Fix #2 — StatsManager concurrent mutation (Critical)
  - Write `StatsManagerConcurrencyTest.java` → RED
  - Fix `StatsManager.scheduleFlush()` to snapshot synchronously → GREEN
  - Git commit: `fix: synchronize StatsManager flush to prevent async mutation race`

Step 3: Fix #3 — KitMenu double-click dupe (High)
  - Write `KitMenuDupeTest.java` → RED
  - Add 50ms per-player debounce in `KitMenu.handleClick()` → GREEN
  - Git commit: `fix: add 50ms debounce to KitMenu clicks to prevent double-click dupe`

Step 4: Fix #4 — PlayerJoinQuitListener async DB access (High)
  - This is a pattern fix — no new test needed (linter reveals unsafe async)
  - Remove `runTaskAsynchronously` wrapper → verify existing tests still GREEN
  - Git commit: `fix: remove unsafe async DB access from PlayerJoinQuitListener`

Step 5: Fix #5 — InventoryClickEvent cancellation order (High)
  - This is a listener ordering fix — covered by existing test assumptions
  - Invert cancellation logic in `DuelListener.onInventoryClick()` → verify existing tests
  - Git commit: `fix: cancel InventoryClickEvent for all non-DRAFTING states before mode-specific logic`

Step 6: Fix #6 — Arena allocation race (High)
  - Write `DuelManagerRaceTest.java` → RED
  - Use `putIfAbsent` in `DuelManager.createDuel()` → GREEN
  - Git commit: `fix: use atomic putIfAbsent for duel player registration`

Step 7: Fix #7 — DuelCommand bestOf validation duplication (Medium)
  - No new test needed (behavior preserved, only deduplication)
  - Extract `validateBestOf()` method → verify all existing tests GREEN
  - Git commit: `refactor: extract validateBestOf method in DuelCommand`

Step 8: Fix #8 — Duel code duplication (Medium)
  - No new test needed (behavior preserved)
  - Extract `recordMatchResult()` and `cleanupAndReturnToLobby()` → verify existing tests GREEN
  - Git commit: `refactor: extract common endDuel/forceEnd logic in Duel`

Step 9: Fix #9 — DaoSupport lazy isMySql (Medium)
  - Write `DaoSupportLazyTest.java` → RED
  - Make isMySql computation lazy → GREEN
  - Git commit: `fix: make DaoSupport MySQL detection lazy to survive startup DB unavailability`

Step 10: Fix #10 — DatabaseConfig empty password validation (Medium)
  - Write `DatabaseConfigValidationTest.java` → RED
  - Add password emptiness check in `DatabaseConfig.fromSection()` → GREEN
  - Git commit: `fix: enforce non-empty database password in config`

Step 11: Fix #11 — KitMenu WeakHashMap replaced with ConcurrentHashMap (Medium)
  - Remove `Collections.newSetFromMap(new WeakHashMap<>())` → use `ConcurrentHashMap.newKeySet()`
  - Add explicit cleanup in `removeMenu()` and `removePlayer()`
  - Verify existing `KitMenu`-related tests GREEN
  - Git commit: `fix: replace WeakHashMap-backed KitMenu activeMenus with ConcurrentHashMap to prevent GC-induced invalidation`

Step 12: Fix #12 — RatingClient log-scrubbing (Medium)
  - Remove the `secret_fp` log line at `RatingClient.java:67-70`
  - Verify existing `RatingClientTest` GREEN
  - Git commit: `fix: remove shared-secret fingerprint from rating request logs`

Step 13: Fix #13 — DuelMode interface annotation (Low)
  - Add `@org.jetbrains.annotations.NotNull` to `DuelMode.getModification()`
  - No behavior change, verify compile passes
  - Git commit: `docs: annotate DuelMode.getModification() as @NotNull`

Step 14: Final verification
  - Run full test suite: `mvn test`
  - Verify all 21+ tests pass
  - Verify no regressions in existing 15 test files
  - Create TDD evidence report at `docs/testing/duel-rustcore-audit.tdd.md`
  - Git commit: `test: add TDD evidence report for 14-fix audit`