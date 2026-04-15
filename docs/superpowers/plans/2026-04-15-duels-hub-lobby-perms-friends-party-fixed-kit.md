# RustCore-Duels: Hub/Lobby Swap, Permissions, Placeholders, Social, Fixed-Kit Modes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rework "hub" vs "lobby" semantics, add proper per-subcommand permissions, fix queue placeholders, implement friend/party/privacy-settings subsystems, and add fixed-kit (NoDebuff-style) mode support with per-player layout customization.

**Architecture:** Incremental. Existing Paper plugin patterns preserved: `CC.parse`, `plugin.getMessage()`, MiniMessage config. New subsystems (friends, party, settings, kit-layouts) each get their own package + manager + command + on-disk YAML. Fixed-kit mode is a second implementation of `DuelMode` interface alongside `KitBuilderMode`.

**Tech Stack:** Java 21, Paper API 1.21.11, Bukkit YAML config, PlaceholderAPI, BungeeCord messaging channel.

**Pre-flight reality check (performed during planning):**
- `Duel.java:343` already gates `recordResult` on both players being ranked — so challenge duels (where `rankedPreference` defaults to `false`) already skip ELO. TASK 5 still adds an explicit `ranked` flag on `DuelRequest` for correctness & future-proofing.
- `DuelMode` interface at `mode/DuelMode.java` already has `onRoundStart(Duel)` — no need to add the hook; FixedKitMode just implements it.
- `sendToLobby` has 5 callers: `SlimeArenaManager.java:172,205`, `Duel.java:359,439`, `LobbyListener.java:125`, `LobbyManager.java:109`.
- `HubCommand` at `command/HubCommand.java:36` reads BungeeCord target from `hub.server` — this is the key we need to rename to `lobby.server` once semantics swap.
- `DuelsExpansion.resolveQueue` calls `queue.getQueueSize(modeId)` but `DuelManager.queuePlayer` stores under `modeId+":ranked"/":unranked"` — confirmed bug.
- Queue keys always include the suffix; there is no non-suffixed entry.

---

## File Structure (Touched / Created)

### Renamed / heavily modified
- `src/main/java/net/rustcore/duel/command/HubCommand.java` — becomes "spawn on duels server" command; adds `/hub [n]` numeric argument; **removes** BungeeCord code.
- `src/main/java/net/rustcore/duel/lobby/LobbyManager.java` — rename `lobbySpawn` → `hubSpawns` (list), `sendToLobby` → `sendToHub(Player)` + `sendToHub(Player, int)`, `getLobbySpawn` → `getHubSpawn(int)`, `loadLobbySpawn` → `loadHubSpawns`. Config keys move from `general.lobby-world` / `general.lobby-spawn.*` to `hubs:` list.
- `src/main/java/net/rustcore/duel/DuelsPlugin.java` — register new commands + new managers; rename `getLobbyManager` calls remain (class name unchanged). Adds getters for FriendManager, PartyManager, SettingsManager, KitLayoutManager.
- `src/main/java/net/rustcore/duel/placeholder/DuelsExpansion.java` — fix queue size calc, add `queue_ranked`.
- `src/main/java/net/rustcore/duel/command/DuelCommand.java` — add fine-grained permission guards per subcommand.
- `src/main/java/net/rustcore/duel/duel/DuelManager.java` — `DuelRequest` gets `ranked` field; privacy integration.
- `src/main/java/net/rustcore/duel/mode/ModeManager.java` — dispatch on `type` field when loading mode configs.
- `src/main/resources/plugin.yml` — new commands (`lobby`, `f`, `party`, `dsettings`, `kitlayout`), new permissions.
- `src/main/resources/config.yml` — new `hubs:` list, new `lobby.server`, new message keys, remove `general.lobby-world` / `general.lobby-spawn`.

### Newly created
- `src/main/java/net/rustcore/duel/command/LobbyCommand.java` — BungeeCord connect (the OLD HubCommand behaviour), reads `lobby.server`.
- `src/main/java/net/rustcore/duel/friend/FriendManager.java`
- `src/main/java/net/rustcore/duel/friend/FriendRequest.java`
- `src/main/java/net/rustcore/duel/command/FriendCommand.java`
- `src/main/java/net/rustcore/duel/party/Party.java`
- `src/main/java/net/rustcore/duel/party/PartyManager.java`
- `src/main/java/net/rustcore/duel/command/PartyCommand.java`
- `src/main/java/net/rustcore/duel/settings/PlayerSettings.java`
- `src/main/java/net/rustcore/duel/settings/SettingsManager.java`
- `src/main/java/net/rustcore/duel/command/SettingsCommand.java`
- `src/main/java/net/rustcore/duel/mode/impl/FixedKitMode.java`
- `src/main/java/net/rustcore/duel/kit/KitLayout.java`
- `src/main/java/net/rustcore/duel/kit/KitLayoutManager.java`
- `src/main/java/net/rustcore/duel/listener/KitLayoutEditorListener.java`
- `src/main/java/net/rustcore/duel/command/KitLayoutCommand.java`
- `src/main/resources/modes/nodebuff.yml` — example fixed-kit mode.

### Rules about the work
- No speculative comments, no Javadoc on new one-liners, no refactor outside each task's stated files.
- Use `CC.parse(plugin.getMessage("key"), "{placeholder}", value)` pattern (already used across codebase).
- Commit at end of each task group (groups labelled in each task header).
- After every batch of file edits, run `./gradlew compileJava` (Paper plugin's build config — group by commit point).

---

## TASK 1 — Swap Hub ↔ Lobby terminology, add `/hub [n]` multi-spawn

**Group commit after this task.**

**Files:**
- Modify: `src/main/java/net/rustcore/duel/lobby/LobbyManager.java`
- Modify: `src/main/java/net/rustcore/duel/command/HubCommand.java`
- Create: `src/main/java/net/rustcore/duel/command/LobbyCommand.java`
- Modify: `src/main/java/net/rustcore/duel/DuelsPlugin.java`
- Modify: `src/main/java/net/rustcore/duel/duel/Duel.java` — `sendToLobby` → `sendToHub` (lines 359, 439)
- Modify: `src/main/java/net/rustcore/duel/arena/SlimeArenaManager.java` — lines 172, 205
- Modify: `src/main/java/net/rustcore/duel/listener/LobbyListener.java` — line 125
- Modify: `src/main/resources/plugin.yml`
- Modify: `src/main/resources/config.yml`

- [ ] **Step 1.1: Update `LobbyManager.java` — field + load method**

Replace the single-spawn field + loader with a list.

Change field at line 26:
```java
// old:
private Location lobbySpawn;
// new:
private final java.util.List<org.bukkit.Location> hubSpawns = new java.util.ArrayList<>();
```

Replace `loadLobbySpawn()` (lines 169–186) with `loadHubSpawns()`:
```java
private void loadHubSpawns() {
    hubSpawns.clear();
    java.util.List<java.util.Map<?, ?>> raw = plugin.getConfig().getMapList("hubs");
    if (raw.isEmpty()) {
        plugin.getLogger().warning("No 'hubs:' entries in config.yml — /hub has no targets.");
        return;
    }
    for (java.util.Map<?, ?> entry : raw) {
        String worldName = String.valueOf(entry.getOrDefault("world", "lobby"));
        double x = toDouble(entry.get("x"), 0.5);
        double y = toDouble(entry.get("y"), 64.0);
        double z = toDouble(entry.get("z"), 0.5);
        float yaw = (float) toDouble(entry.get("yaw"), 0.0);
        float pitch = (float) toDouble(entry.get("pitch"), 0.0);
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Hub world '" + worldName + "' not loaded yet; will retry lazily.");
            hubSpawns.add(new Location(null, x, y, z, yaw, pitch));
            continue;
        }
        hubSpawns.add(new Location(world, x, y, z, yaw, pitch));
    }
    plugin.getLogger().info("Loaded " + hubSpawns.size() + " hub spawn(s)");
}

private static double toDouble(Object o, double def) {
    if (o instanceof Number n) return n.doubleValue();
    if (o instanceof String s) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; }
    }
    return def;
}
```

Replace the `load()` call at line 43 (`loadLobbySpawn();`) with `loadHubSpawns();`.

- [ ] **Step 1.2: Update `LobbyManager.java` — rename sendToLobby + add overload**

Replace `sendToLobby(Player)` (lines 109–132) with two methods:

```java
public void sendToHub(Player player) {
    sendToHub(player, 0);
}

public void sendToHub(Player player, int index) {
    player.getInventory().clear();
    player.getInventory().setArmorContents(new ItemStack[4]);
    player.getInventory().setItemInOffHand(null);
    player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
    player.setFoodLevel(20);
    player.setSaturation(20f);
    player.setFireTicks(0);
    player.setFallDistance(0);
    player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
    player.setAllowFlight(false);
    player.setFlying(false);
    player.setGameMode(GameMode.ADVENTURE);

    Location spawn = getHubSpawn(index);
    if (spawn != null) {
        player.teleport(spawn);
    }
    giveLobbyItems(player);
}
```

Replace `getLobbySpawn()` (lines 188–193) with:

```java
public Location getHubSpawn(int index) {
    if (hubSpawns.isEmpty()) { loadHubSpawns(); }
    if (index < 0 || index >= hubSpawns.size()) return null;
    Location loc = hubSpawns.get(index);
    if (loc.getWorld() == null) {
        // Re-resolve world lazily
        java.util.List<java.util.Map<?, ?>> raw = plugin.getConfig().getMapList("hubs");
        if (index < raw.size()) {
            String worldName = String.valueOf(raw.get(index).getOrDefault("world", "lobby"));
            World w = Bukkit.getWorld(worldName);
            if (w != null) {
                loc = new Location(w, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
                hubSpawns.set(index, loc);
            }
        }
    }
    return loc.getWorld() != null ? loc : null;
}

public int getHubCount() { return hubSpawns.size(); }
```

- [ ] **Step 1.3: Update callers of `sendToLobby` → `sendToHub`**

Exact edits:
- `src/main/java/net/rustcore/duel/duel/Duel.java:359` → `plugin.getLobbyManager().sendToHub(player);`
- `src/main/java/net/rustcore/duel/duel/Duel.java:439` → `plugin.getLobbyManager().sendToHub(player);`
- `src/main/java/net/rustcore/duel/arena/SlimeArenaManager.java:172` → `plugin.getLobbyManager().sendToHub(player);`
- `src/main/java/net/rustcore/duel/arena/SlimeArenaManager.java:205` → `plugin.getLobbyManager().sendToHub(player);`
- `src/main/java/net/rustcore/duel/listener/LobbyListener.java:125` → `plugin.getLobbyManager().sendToHub(player);`

- [ ] **Step 1.4: Rewrite `HubCommand.java` — hub = on-server spawn with `[n]` arg**

Replace whole file:

```java
package net.rustcore.duel.command;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.util.CC;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HubCommand implements CommandExecutor {

    private final DuelsPlugin plugin;

    public HubCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CC.parse(plugin.getMessage("only-players")));
            return true;
        }
        if (!player.hasPermission("duels.hub")) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("no-permission"))));
            return true;
        }

        if (plugin.getDuelManager().isInDuel(player.getUniqueId())) {
            plugin.getDuelManager().forfeitDuel(player);
        }
        plugin.getDuelManager().getQueue().removePlayer(player.getUniqueId());

        int index = 0;
        if (args.length >= 1) {
            try {
                index = Integer.parseInt(args[0]) - 1;
            } catch (NumberFormatException e) {
                player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                        .append(CC.parse(plugin.getMessage("hub-invalid-number"))));
                return true;
            }
        }

        int count = plugin.getLobbyManager().getHubCount();
        if (index < 0 || index >= count) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("hub-out-of-range"),
                            "{n}", String.valueOf(index + 1),
                            "{max}", String.valueOf(count))));
            return true;
        }

        plugin.getLobbyManager().sendToHub(player, index);
        return true;
    }
}
```

- [ ] **Step 1.5: Create `LobbyCommand.java` — BungeeCord connect**

Write to `src/main/java/net/rustcore/duel/command/LobbyCommand.java`:

```java
package net.rustcore.duel.command;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.util.CC;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LobbyCommand implements CommandExecutor {

    private final DuelsPlugin plugin;

    public LobbyCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CC.parse(plugin.getMessage("only-players")));
            return true;
        }
        if (!player.hasPermission("duels.lobby")) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("no-permission"))));
            return true;
        }

        // Leaving the duel server entirely — forfeit first so state is clean
        if (plugin.getDuelManager().isInDuel(player.getUniqueId())) {
            plugin.getDuelManager().forfeitDuel(player);
        }
        plugin.getDuelManager().getQueue().removePlayer(player.getUniqueId());

        String server = plugin.getConfig().getString("lobby.server", "lobby");
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);
        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        return true;
    }
}
```

- [ ] **Step 1.6: Update `DuelsPlugin.java` — register LobbyCommand**

At line 69 (after `getCommand("hub").setExecutor(new HubCommand(this));`), add:
```java
getCommand("lobby").setExecutor(new LobbyCommand(this));
```

- [ ] **Step 1.7: Update `plugin.yml` commands block**

Replace the `hub:` block and append `lobby:`:

```yaml
commands:
  duel:
    description: Main duel command
    usage: /<command> <subcommand>
    aliases: [ duels ]
  hub:
    description: Teleport to a hub spawn on this server
    usage: /<command> [number]
    aliases: [ spawn ]
  lobby:
    description: Return to the network lobby server
    usage: /<command>
```

(Note: `lobby` and `spawn` no longer alias `hub`. `lobby` is now its own command.)

- [ ] **Step 1.8: Update `config.yml` — remove old keys, add new**

Remove the `general:` → `lobby-world` / `lobby-spawn` block. Add:

```yaml
# BungeeCord target server name for /lobby
lobby:
  server: "lobby"

# Hub spawns on THIS server. /hub <n> teleports to the nth entry (1-based).
hubs:
  - world: "lobby"
    x: 0.5
    y: 64.0
    z: 0.5
    yaw: 0.0
    pitch: 0.0
```

Also add these under `messages:`:
```yaml
  hub-invalid-number: "<red>Hub index must be a number."
  hub-out-of-range: "<red>Hub <white><n><red> doesn't exist (max: <white><max><red>)."
```

- [ ] **Step 1.9: Verify compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 1.10: Commit**

```bash
git add -A
git commit -m "refactor: swap hub/lobby semantics and add /hub [n] multi-spawn

- /hub now teleports to an on-server spawn (1..N), config via hubs: list
- /lobby now handles BungeeCord connect (config: lobby.server)
- rename LobbyManager.sendToLobby -> sendToHub, getLobbySpawn -> getHubSpawn
- update all callers in Duel, SlimeArenaManager, LobbyListener"
```

---

## TASK 2 — Fine-grained permissions per subcommand

**Files:**
- Modify: `src/main/resources/plugin.yml` — permissions section
- Modify: `src/main/java/net/rustcore/duel/command/DuelCommand.java`

- [ ] **Step 2.1: Update `plugin.yml` permissions block**

Replace the existing permissions block with:

```yaml
permissions:
  duels.admin:
    description: Admin permission for duel management
    default: op
  duels.play:
    description: Permission to play duels
    default: true
  duels.queue:
    description: Permission to join matchmaking queue
    default: true
  duels.challenge:
    description: Permission to challenge / accept / decline duels
    default: true
  duels.stats:
    description: Permission to view duel stats
    default: true
  duels.ranked:
    description: Permission to toggle ranked mode
    default: true
  duels.draftmenu:
    description: Permission to open the kit draft menu
    default: true
  duels.hub:
    description: Permission to use /hub
    default: true
  duels.lobby:
    description: Permission to use /lobby
    default: true
```

(Further permissions for `duels.friends`, `duels.party`, `duels.settings`, `duels.kitlayout` are added in their respective tasks.)

- [ ] **Step 2.2: Add a helper at top of `DuelCommand.java`**

Near the other private methods, add:

```java
private boolean requirePerm(Player player, String node) {
    if (player.hasPermission(node)) return true;
    player.sendMessage(CC.parse(plugin.getMessage("prefix"))
            .append(CC.parse(plugin.getMessage("no-permission"))));
    return false;
}
```

- [ ] **Step 2.3: Update permission checks in each subcommand handler**

In `handleChallenge` at line 225, change:
```java
if (!player.hasPermission("duels.queue")) {
```
to:
```java
if (!requirePerm(player, "duels.challenge")) return true;
```
(remove the old if+message block, replace with single call).

In `handleAccept` at line 293, replace `duels.queue` with `duels.challenge`, using `requirePerm`.

In `handleDecline` (line 302), add at top after player cast:
```java
if (!requirePerm(player, "duels.challenge")) return true;
```

In `handleStats` at line 328, add at top after player cast:
```java
if (!requirePerm(player, "duels.stats")) return true;
```

In `handleRankedToggle` at line 309, add at top:
```java
if (!requirePerm(player, "duels.ranked")) return true;
```

In `handleOpenKitMenu` at line 60, add at top:
```java
if (!requirePerm(player, "duels.draftmenu")) return true;
```

In `handleLeave` at line 204, add at top:
```java
if (!requirePerm(player, "duels.play")) return true;
```

Leave `handleQueue` `duels.queue` guard as-is (line 155 guards *target* not sender; keep that). Ensure sender also needs `duels.queue` at top of `handleQueue` — it's already implied via that existing check pattern; verify and leave unchanged if present.

- [ ] **Step 2.4: Verify compile**

Run: `./gradlew compileJava` — expect BUILD SUCCESSFUL.

- [ ] **Step 2.5: Commit**

```bash
git add src/main/resources/plugin.yml src/main/java/net/rustcore/duel/command/DuelCommand.java
git commit -m "feat: add fine-grained permissions per /duel subcommand"
```

---

## TASK 3 — Fix `%duels_queue_size%` + `%duels_queue_size_<mode>%`

**Files:**
- Modify: `src/main/java/net/rustcore/duel/placeholder/DuelsExpansion.java`

- [ ] **Step 3.1: Fix `resolveQueue` total size + per-mode size**

In `DuelsExpansion.java`, replace the two blocks at lines 278–290:

```java
if (rest.equalsIgnoreCase("size")) {
    int total = plugin.getModeManager().getAllModes().stream()
            .mapToInt(m -> queue.getQueueSize(m.getId() + ":ranked")
                         + queue.getQueueSize(m.getId() + ":unranked"))
            .sum();
    return String.valueOf(total);
}

if (rest.toLowerCase().startsWith("size_")) {
    String modeId = rest.substring(5).toLowerCase();
    int size = queue.getQueueSize(modeId + ":ranked")
             + queue.getQueueSize(modeId + ":unranked");
    return String.valueOf(size);
}
```

- [ ] **Step 3.2: Verify compile**

Run: `./gradlew compileJava` — expect BUILD SUCCESSFUL.

- [ ] **Step 3.3: Manual smoke-test note (no commit yet — batch with Task 4)**

Start server, `/duel queue kitbuilder`, check `%duels_queue_size%` via PlaceholderAPI parse. Expect `1` not `0`.

---

## TASK 4 — Add `%duels_queue_ranked%` placeholder

**Files:**
- Modify: `src/main/java/net/rustcore/duel/placeholder/DuelsExpansion.java`

- [ ] **Step 4.1: Add ranked case before the `return EMPTY` at end of `resolveQueue`**

Insert before the final `return EMPTY;`:

```java
if (rest.equalsIgnoreCase("ranked")) {
    boolean ranked = plugin.getDuelManager().isRanked(player.getUniqueId());
    return ranked ? "RANKED" : "UNRANKED";
}
```

- [ ] **Step 4.2: Update the class-level Javadoc for queue placeholders**

Under the `<h3>Queue placeholders:</h3>` block, add:
```
 *   %duels_queue_ranked%              → RANKED / UNRANKED
```

- [ ] **Step 4.3: Verify compile**

Run: `./gradlew compileJava` — expect BUILD SUCCESSFUL.

- [ ] **Step 4.4: Commit (Tasks 3 + 4 together)**

```bash
git add src/main/java/net/rustcore/duel/placeholder/DuelsExpansion.java
git commit -m "fix(papi): correct queue_size to account for ranked/unranked suffix + add queue_ranked"
```

---

## TASK 5 — Force unranked on `/duel challenge`

Reality: `Duel.java:343` already gates ELO on both players having `isRanked=true`, and `rankedPreference` defaults `false`, so challenges already skip ELO. Still, make the intent explicit by pinning the request to unranked regardless of preference.

**Files:**
- Modify: `src/main/java/net/rustcore/duel/duel/DuelManager.java`
- Modify: `src/main/java/net/rustcore/duel/duel/Duel.java`

- [ ] **Step 5.1: Add `ranked` field to `DuelRequest` record**

At line 357 in `DuelManager.java`, change the record:

```java
public record DuelRequest(UUID senderId, UUID targetId, String modeId, int bestOf, boolean ranked) {}
```

- [ ] **Step 5.2: Update `sendRequest` to build request with `ranked=false`**

In `DuelManager.java` at line 120 replace the constructor call:
```java
DuelRequest request = new DuelRequest(sender.getUniqueId(), target.getUniqueId(), modeId, bestOf, false);
```

- [ ] **Step 5.3: Pass `ranked` through `acceptRequest` → `createDuel` via metadata**

In `acceptRequest` replace the final `createDuel(...)` call at line 181 with:

```java
Duel duel = createDuel(request.modeId(), List.of(sender, target), request.bestOf());
if (duel != null) {
    duel.setMeta("forced-unranked", !request.ranked());
}
```

Change `createDuel` signature at line 203 to return the created `Duel` (or `null` on failure). Add `return duel;` at the end after starting it, and `return null;` on the error paths. If the method currently returns `void`, change it to `public Duel createDuel(...)`. (Check the code — if internal callers ignore the return, that's fine.)

- [ ] **Step 5.4: Teach `Duel.endDuel` to honour `forced-unranked`**

In `Duel.java` around line 342, change:

```java
boolean isRanked = plugin.getDuelManager().isRanked(winnerId) && plugin.getDuelManager().isRanked(loserId);
```

to:

```java
Object forcedUnranked = getMeta("forced-unranked");
boolean isRanked = !(forcedUnranked instanceof Boolean b && b)
        && plugin.getDuelManager().isRanked(winnerId)
        && plugin.getDuelManager().isRanked(loserId);
```

Also update `forceEnd(UUID)` (line 400) similarly at line 433: wrap `recordResult` in the same `!forcedUnranked` check.

- [ ] **Step 5.5: Verify compile**

Run: `./gradlew compileJava` — expect BUILD SUCCESSFUL.

- [ ] **Step 5.6: Commit**

```bash
git add src/main/java/net/rustcore/duel/duel/DuelManager.java src/main/java/net/rustcore/duel/duel/Duel.java
git commit -m "feat: force unranked on /duel challenge requests (no ELO on challenge duels)"
```

---

## TASK 6 — Friend system + `/f`

**Group commit after this task.**

**Files:**
- Create: `src/main/java/net/rustcore/duel/friend/FriendManager.java`
- Create: `src/main/java/net/rustcore/duel/friend/FriendRequest.java`
- Create: `src/main/java/net/rustcore/duel/command/FriendCommand.java`
- Modify: `src/main/java/net/rustcore/duel/DuelsPlugin.java`
- Modify: `src/main/resources/plugin.yml`
- Modify: `src/main/resources/config.yml`

- [ ] **Step 6.1: Create `FriendRequest.java`**

```java
package net.rustcore.duel.friend;

import java.util.UUID;

public record FriendRequest(UUID senderId, UUID targetId, long expiresAtMs) {
    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAtMs;
    }
}
```

- [ ] **Step 6.2: Create `FriendManager.java`**

```java
package net.rustcore.duel.friend;

import net.rustcore.duel.DuelsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FriendManager {

    private static final long REQUEST_TTL_MS = 60_000;

    private final DuelsPlugin plugin;
    private final Map<UUID, Set<UUID>> friends = new ConcurrentHashMap<>();
    private final Map<UUID, FriendRequest> pendingByTarget = new ConcurrentHashMap<>();
    private final File file;

    public FriendManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/friends.yml");
    }

    public void load() {
        friends.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yml.getConfigurationSection("players");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            UUID uid;
            try { uid = UUID.fromString(key); } catch (IllegalArgumentException e) { continue; }
            Set<UUID> set = ConcurrentHashMap.newKeySet();
            for (String other : section.getStringList(key + ".friends")) {
                try { set.add(UUID.fromString(other)); } catch (IllegalArgumentException ignored) {}
            }
            friends.put(uid, set);
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Set<UUID>> e : friends.entrySet()) {
            List<String> list = new ArrayList<>();
            for (UUID f : e.getValue()) list.add(f.toString());
            yml.set("players." + e.getKey() + ".friends", list);
        }
        try {
            file.getParentFile().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save friends.yml: " + ex.getMessage());
        }
    }

    public Set<UUID> getFriends(UUID player) {
        return friends.getOrDefault(player, Set.of());
    }

    public boolean isFriend(UUID a, UUID b) {
        return getFriends(a).contains(b);
    }

    public boolean addFriend(UUID a, UUID b) {
        if (a.equals(b)) return false;
        friends.computeIfAbsent(a, k -> ConcurrentHashMap.newKeySet()).add(b);
        friends.computeIfAbsent(b, k -> ConcurrentHashMap.newKeySet()).add(a);
        save();
        return true;
    }

    public boolean removeFriend(UUID a, UUID b) {
        boolean changed = false;
        Set<UUID> sa = friends.get(a);
        if (sa != null && sa.remove(b)) changed = true;
        Set<UUID> sb = friends.get(b);
        if (sb != null && sb.remove(a)) changed = true;
        if (changed) save();
        return changed;
    }

    public boolean sendRequest(UUID from, UUID to) {
        if (from.equals(to)) return false;
        if (isFriend(from, to)) return false;
        pendingByTarget.put(to, new FriendRequest(from, to, System.currentTimeMillis() + REQUEST_TTL_MS));
        return true;
    }

    public FriendRequest consumePending(UUID target) {
        FriendRequest req = pendingByTarget.remove(target);
        if (req == null || req.isExpired()) return null;
        return req;
    }

    public FriendRequest peekPending(UUID target) {
        FriendRequest req = pendingByTarget.get(target);
        if (req == null) return null;
        if (req.isExpired()) { pendingByTarget.remove(target); return null; }
        return req;
    }
}
```

- [ ] **Step 6.3: Create `FriendCommand.java`**

```java
package net.rustcore.duel.command;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.friend.FriendRequest;
import net.rustcore.duel.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class FriendCommand implements CommandExecutor {

    private final DuelsPlugin plugin;

    public FriendCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CC.parse(plugin.getMessage("only-players")));
            return true;
        }
        if (!player.hasPermission("duels.friends")) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("no-permission"))));
            return true;
        }

        String sub = args.length == 0 ? "list" : args[0].toLowerCase();
        switch (sub) {
            case "add" -> handleAdd(player, args);
            case "remove", "rm" -> handleRemove(player, args);
            case "accept" -> handleAccept(player);
            case "decline", "deny" -> handleDecline(player);
            case "list" -> handleList(player);
            default -> player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("friend-usage"))));
        }
        return true;
    }

    private void handleAdd(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("friend-usage"))));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("player-not-found"))));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) return;
        if (plugin.getFriendManager().isFriend(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("friend-already"), "{player}", target.getName())));
            return;
        }
        // Privacy gate (Task 8 integration — safe no-op before SettingsManager exists; null-check plugin.getSettingsManager() if needed)
        if (plugin.getSettingsManager() != null
                && !plugin.getSettingsManager().getSettings(target.getUniqueId()).isAcceptFriendRequests()) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("friend-blocked"), "{player}", target.getName())));
            return;
        }
        plugin.getFriendManager().sendRequest(player.getUniqueId(), target.getUniqueId());
        player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("friend-request-sent"), "{player}", target.getName())));
        target.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("friend-request-received"), "{player}", player.getName())));
    }

    private void handleRemove(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("friend-usage"))));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getName() == null) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("player-not-found"))));
            return;
        }
        if (!plugin.getFriendManager().removeFriend(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("friend-not-friend"), "{player}", target.getName())));
            return;
        }
        player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("friend-removed"), "{player}", target.getName())));
    }

    private void handleAccept(Player player) {
        FriendRequest req = plugin.getFriendManager().consumePending(player.getUniqueId());
        if (req == null) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("friend-no-request"))));
            return;
        }
        plugin.getFriendManager().addFriend(player.getUniqueId(), req.senderId());
        Player sender = Bukkit.getPlayer(req.senderId());
        String senderName = sender != null ? sender.getName() : Bukkit.getOfflinePlayer(req.senderId()).getName();
        player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("friend-added"), "{player}", senderName != null ? senderName : "?")));
        if (sender != null) {
            sender.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("friend-added"), "{player}", player.getName())));
        }
    }

    private void handleDecline(Player player) {
        FriendRequest req = plugin.getFriendManager().consumePending(player.getUniqueId());
        if (req == null) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("friend-no-request"))));
            return;
        }
        player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("friend-declined"))));
    }

    private void handleList(Player player) {
        var set = plugin.getFriendManager().getFriends(player.getUniqueId());
        player.sendMessage(CC.parse(plugin.getMessage("friend-list-header"),
                "{count}", String.valueOf(set.size())));
        for (UUID uid : set) {
            Player online = Bukkit.getPlayer(uid);
            if (online != null) {
                player.sendMessage(CC.parse(plugin.getMessage("friend-list-entry-online"),
                        "{player}", online.getName()));
            } else {
                OfflinePlayer op = Bukkit.getOfflinePlayer(uid);
                player.sendMessage(CC.parse(plugin.getMessage("friend-list-entry-offline"),
                        "{player}", op.getName() != null ? op.getName() : uid.toString().substring(0, 8)));
            }
        }
    }
}
```

- [ ] **Step 6.4: Register in `DuelsPlugin.java`**

Add field: `private FriendManager friendManager;`

In `onEnable()` after lobbyManager init:
```java
friendManager = new FriendManager(this);
friendManager.load();
```

In command registration block:
```java
getCommand("f").setExecutor(new FriendCommand(this));
```

Add getter:
```java
public FriendManager getFriendManager() { return friendManager; }
```

In `onDisable()` (if exists) add `if (friendManager != null) friendManager.save();`.

- [ ] **Step 6.5: Register command + permission in `plugin.yml`**

Under `commands:`:
```yaml
  f:
    description: Friend system
    usage: /<command> [add|remove|list|accept|decline] [player]
    aliases: [ friend, friends ]
```

Under `permissions:`:
```yaml
  duels.friends:
    description: Use friend system
    default: true
```

- [ ] **Step 6.6: Add friend messages to `config.yml`**

Under `messages:` append:

```yaml
  friend-usage: "<gray>Usage: <white>/f <add|remove|list|accept|decline> [player]"
  friend-request-sent: "<green>Friend request sent to <white><player><green>."
  friend-request-received: "<yellow><player> <green>sent you a friend request. <white>/f accept <gray>to accept."
  friend-request-expired: "<gray>Friend request from <white><player> <gray>expired."
  friend-added: "<green>You are now friends with <white><player><green>."
  friend-removed: "<gray>Removed <white><player> <gray>from friends."
  friend-declined: "<gray>Friend request declined."
  friend-already: "<red>Already friends with <white><player><red>."
  friend-not-friend: "<red><player> <red>is not your friend."
  friend-no-request: "<red>No pending friend request."
  friend-list-header: "<gold>Friends (<count>):"
  friend-list-entry-online: " <green>● <white><player>"
  friend-list-entry-offline: " <gray>○ <white><player>"
  friend-blocked: "<red><player> <red>is not accepting friend requests."
```

- [ ] **Step 6.7: Verify compile**

Run: `./gradlew compileJava` — expect BUILD SUCCESSFUL. If `plugin.getSettingsManager()` isn't defined yet, temporarily stub with `// TODO settings` — but SettingsManager arrives in Task 8 so keep a **null-guarded** call (`plugin.getSettingsManager() != null`) to compile cleanly. Add temporary stub getter in DuelsPlugin:

```java
public net.rustcore.duel.settings.SettingsManager getSettingsManager() { return null; }
```

This will be replaced in Task 8.

- [ ] **Step 6.8: Commit**

```bash
git add -A
git commit -m "feat: add friend system with /f add|remove|accept|decline|list"
```

---

## TASK 7 — Party system (2–5 players, unranked only, commands only — no queue integration yet)

**Files:**
- Create: `src/main/java/net/rustcore/duel/party/Party.java`
- Create: `src/main/java/net/rustcore/duel/party/PartyManager.java`
- Create: `src/main/java/net/rustcore/duel/command/PartyCommand.java`
- Modify: `src/main/java/net/rustcore/duel/DuelsPlugin.java`
- Modify: `src/main/resources/plugin.yml`
- Modify: `src/main/resources/config.yml`

- [ ] **Step 7.1: Create `Party.java`**

```java
package net.rustcore.duel.party;

import java.util.*;

public class Party {

    public static final int MAX_SIZE = 5;

    private final UUID partyId = UUID.randomUUID();
    private UUID leaderId;
    private final Set<UUID> members = new LinkedHashSet<>();

    public Party(UUID leaderId) {
        this.leaderId = leaderId;
        this.members.add(leaderId);
    }

    public UUID getPartyId() { return partyId; }
    public UUID getLeaderId() { return leaderId; }
    public Set<UUID> getMembers() { return Collections.unmodifiableSet(members); }
    public int getSize() { return members.size(); }
    public boolean isFull() { return members.size() >= MAX_SIZE; }
    public boolean isMember(UUID id) { return members.contains(id); }
    public boolean isLeader(UUID id) { return leaderId.equals(id); }

    public boolean addMember(UUID id) {
        if (isFull() || members.contains(id)) return false;
        members.add(id);
        return true;
    }

    public boolean removeMember(UUID id) {
        boolean removed = members.remove(id);
        if (removed && id.equals(leaderId) && !members.isEmpty()) {
            leaderId = members.iterator().next();
        }
        return removed;
    }
}
```

- [ ] **Step 7.2: Create `PartyManager.java`**

```java
package net.rustcore.duel.party;

import net.rustcore.duel.DuelsPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PartyManager {

    private static final long INVITE_TTL_MS = 60_000;

    private final DuelsPlugin plugin;
    private final Map<UUID, Party> playerParty = new ConcurrentHashMap<>();
    private final Map<UUID, Invite> pendingInvites = new ConcurrentHashMap<>();

    public PartyManager(DuelsPlugin plugin) { this.plugin = plugin; }

    public Party getParty(UUID player) { return playerParty.get(player); }
    public boolean isInParty(UUID player) { return playerParty.containsKey(player); }

    public Party createParty(UUID leader) {
        Party existing = playerParty.get(leader);
        if (existing != null) return existing;
        Party p = new Party(leader);
        playerParty.put(leader, p);
        return p;
    }

    public void disbandParty(UUID leader) {
        Party party = playerParty.get(leader);
        if (party == null || !party.isLeader(leader)) return;
        for (UUID member : new ArrayList<>(party.getMembers())) {
            playerParty.remove(member);
        }
    }

    public boolean invite(UUID leader, UUID target) {
        Party party = createParty(leader);
        if (!party.isLeader(leader)) return false;
        if (party.isFull()) return false;
        if (playerParty.containsKey(target)) return false;
        pendingInvites.put(target, new Invite(leader, target, System.currentTimeMillis() + INVITE_TTL_MS));
        return true;
    }

    public Invite consumeInvite(UUID target) {
        Invite inv = pendingInvites.remove(target);
        if (inv == null || System.currentTimeMillis() > inv.expiresAtMs) return null;
        return inv;
    }

    public boolean acceptInvite(UUID target) {
        Invite inv = consumeInvite(target);
        if (inv == null) return false;
        Party party = playerParty.get(inv.leaderId);
        if (party == null || party.isFull()) return false;
        party.addMember(target);
        playerParty.put(target, party);
        return true;
    }

    public boolean leaveParty(UUID member) {
        Party party = playerParty.remove(member);
        if (party == null) return false;
        party.removeMember(member);
        if (party.getSize() <= 1) {
            // Last player left → dissolve the party
            for (UUID u : new ArrayList<>(party.getMembers())) {
                playerParty.remove(u);
            }
        }
        return true;
    }

    public boolean kickMember(UUID leader, UUID target) {
        Party party = playerParty.get(leader);
        if (party == null || !party.isLeader(leader) || !party.isMember(target)) return false;
        if (leader.equals(target)) return false;
        party.removeMember(target);
        playerParty.remove(target);
        return true;
    }

    public record Invite(UUID leaderId, UUID targetId, long expiresAtMs) {}
}
```

- [ ] **Step 7.3: Create `PartyCommand.java`**

```java
package net.rustcore.duel.command;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.party.Party;
import net.rustcore.duel.party.PartyManager;
import net.rustcore.duel.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class PartyCommand implements CommandExecutor {

    private final DuelsPlugin plugin;

    public PartyCommand(DuelsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CC.parse(plugin.getMessage("only-players")));
            return true;
        }
        if (!player.hasPermission("duels.party")) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("no-permission"))));
            return true;
        }

        String sub = args.length == 0 ? "info" : args[0].toLowerCase();
        PartyManager pm = plugin.getPartyManager();
        switch (sub) {
            case "invite" -> handleInvite(player, args, pm);
            case "accept" -> handleAccept(player, pm);
            case "decline", "deny" -> handleDecline(player, pm);
            case "leave" -> handleLeave(player, pm);
            case "kick" -> handleKick(player, args, pm);
            case "disband" -> handleDisband(player, pm);
            case "list", "info" -> handleList(player, pm);
            default -> player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("party-usage"))));
        }
        return true;
    }

    private void handleInvite(Player player, String[] args, PartyManager pm) {
        if (args.length < 2) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("party-usage"))));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("player-not-found"))));
            return;
        }
        // Privacy gate
        if (plugin.getSettingsManager() != null) {
            var s = plugin.getSettingsManager().getSettings(target.getUniqueId());
            if (s.getStatus() == net.rustcore.duel.settings.PlayerSettings.Status.DO_NOT_DISTURB) return;
            var vis = s.getWhoCanInviteToParty();
            if (vis == net.rustcore.duel.settings.PlayerSettings.Visibility.NOBODY) {
                player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                        .append(CC.parse(plugin.getMessage("party-invite-blocked"), "{player}", target.getName())));
                return;
            }
            if (vis == net.rustcore.duel.settings.PlayerSettings.Visibility.FRIENDS_ONLY
                    && !plugin.getFriendManager().isFriend(player.getUniqueId(), target.getUniqueId())) {
                player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                        .append(CC.parse(plugin.getMessage("party-invite-blocked"), "{player}", target.getName())));
                return;
            }
        }
        if (!pm.invite(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("party-invite-failed"))));
            return;
        }
        player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("party-invite-sent"), "{player}", target.getName())));
        target.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("party-invite-received"), "{player}", player.getName())));
    }

    private void handleAccept(Player player, PartyManager pm) {
        if (!pm.acceptInvite(player.getUniqueId())) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("party-no-invite"))));
            return;
        }
        Party party = pm.getParty(player.getUniqueId());
        for (UUID uid : party.getMembers()) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) {
                p.sendMessage(CC.parse(plugin.getMessage("prefix"))
                        .append(CC.parse(plugin.getMessage("party-joined"), "{player}", player.getName())));
            }
        }
    }

    private void handleDecline(Player player, PartyManager pm) {
        var inv = pm.consumeInvite(player.getUniqueId());
        if (inv == null) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("party-no-invite"))));
            return;
        }
        player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("party-declined"))));
    }

    private void handleLeave(Player player, PartyManager pm) {
        if (!pm.leaveParty(player.getUniqueId())) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("party-not-in-party"))));
            return;
        }
        player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("party-left"))));
    }

    private void handleKick(Player player, String[] args, PartyManager pm) {
        if (args.length < 2) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("party-usage"))));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getName() == null) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("player-not-found"))));
            return;
        }
        if (!pm.kickMember(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("party-kick-failed"))));
            return;
        }
        player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("party-kicked"), "{player}", target.getName())));
        Player tp = Bukkit.getPlayer(target.getUniqueId());
        if (tp != null) {
            tp.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("party-you-kicked"))));
        }
    }

    private void handleDisband(Player player, PartyManager pm) {
        Party party = pm.getParty(player.getUniqueId());
        if (party == null || !party.isLeader(player.getUniqueId())) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("party-not-leader"))));
            return;
        }
        for (UUID uid : party.getMembers()) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) {
                p.sendMessage(CC.parse(plugin.getMessage("prefix"))
                        .append(CC.parse(plugin.getMessage("party-disbanded"))));
            }
        }
        pm.disbandParty(player.getUniqueId());
    }

    private void handleList(Player player, PartyManager pm) {
        Party party = pm.getParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("party-not-in-party"))));
            return;
        }
        player.sendMessage(CC.parse(plugin.getMessage("party-list-header"),
                "{count}", String.valueOf(party.getSize())));
        for (UUID uid : party.getMembers()) {
            Player p = Bukkit.getPlayer(uid);
            String name = p != null ? p.getName() : Bukkit.getOfflinePlayer(uid).getName();
            if (name == null) name = uid.toString().substring(0, 8);
            boolean leader = party.isLeader(uid);
            player.sendMessage(CC.parse(plugin.getMessage(leader ? "party-list-entry-leader" : "party-list-entry"),
                    "{player}", name));
        }
    }
}
```

- [ ] **Step 7.4: Register PartyManager + command in `DuelsPlugin.java`**

Add field: `private PartyManager partyManager;`

In `onEnable()`:
```java
partyManager = new PartyManager(this);
```

```java
getCommand("party").setExecutor(new PartyCommand(this));
```

Getter:
```java
public PartyManager getPartyManager() { return partyManager; }
```

- [ ] **Step 7.5: Update `plugin.yml`**

Commands:
```yaml
  party:
    description: Party system
    usage: /<command> [invite|accept|decline|leave|kick|disband|list] [player]
    aliases: [ p ]
```

Permissions:
```yaml
  duels.party:
    description: Use party system
    default: true
```

- [ ] **Step 7.6: Add party messages to `config.yml`**

Under `messages:` append:

```yaml
  party-usage: "<gray>Usage: <white>/party <invite|accept|decline|leave|kick|disband|list> [player]"
  party-invite-sent: "<green>Invited <white><player> <green>to your party."
  party-invite-received: "<yellow><player> <green>invited you to a party. <white>/party accept <gray>to join."
  party-invite-failed: "<red>Could not invite player (party full or target already in a party)."
  party-invite-blocked: "<red><player> <red>is not accepting party invites."
  party-no-invite: "<red>No pending party invite."
  party-joined: "<green><player> <green>joined the party."
  party-left: "<gray>You left the party."
  party-kicked: "<gray>Kicked <white><player> <gray>from the party."
  party-you-kicked: "<red>You were kicked from the party."
  party-kick-failed: "<red>Could not kick that player."
  party-not-in-party: "<red>You are not in a party."
  party-not-leader: "<red>Only the party leader can do that."
  party-disbanded: "<gray>Party disbanded."
  party-declined: "<gray>Party invite declined."
  party-list-header: "<gold>Party members (<count>):"
  party-list-entry-leader: " <gold>★ <white><player>"
  party-list-entry: "  <gray>• <white><player>"
```

- [ ] **Step 7.7: Verify compile**

Run: `./gradlew compileJava` — expect BUILD SUCCESSFUL.

- [ ] **Step 7.8: Commit**

```bash
git add -A
git commit -m "feat: add party system (2-5 players) with /party commands"
```

---

## TASK 8 — Player privacy settings + `/dsettings`

**Files:**
- Create: `src/main/java/net/rustcore/duel/settings/PlayerSettings.java`
- Create: `src/main/java/net/rustcore/duel/settings/SettingsManager.java`
- Create: `src/main/java/net/rustcore/duel/command/SettingsCommand.java`
- Modify: `src/main/java/net/rustcore/duel/DuelsPlugin.java`
- Modify: `src/main/java/net/rustcore/duel/duel/DuelManager.java` — integrate privacy into `sendRequest`
- Modify: `src/main/resources/plugin.yml`
- Modify: `src/main/resources/config.yml`

- [ ] **Step 8.1: Create `PlayerSettings.java`**

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

- [ ] **Step 8.2: Create `SettingsManager.java`**

```java
package net.rustcore.duel.settings;

import net.rustcore.duel.DuelsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SettingsManager {

    private final DuelsPlugin plugin;
    private final Map<UUID, PlayerSettings> settings = new ConcurrentHashMap<>();
    private final File file;

    public SettingsManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/player_settings.yml");
    }

    public void load() {
        settings.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yml.getConfigurationSection("players");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            UUID uid;
            try { uid = UUID.fromString(key); } catch (IllegalArgumentException e) { continue; }
            PlayerSettings ps = new PlayerSettings();
            ps.setWhoCanInviteToParty(parseVis(section.getString(key + ".party-invites", "ALL")));
            ps.setWhoCanChallenge(parseVis(section.getString(key + ".challenges", "ALL")));
            ps.setAcceptFriendRequests(section.getBoolean(key + ".friend-requests", true));
            ps.setStatus(parseStatus(section.getString(key + ".status", "ONLINE")));
            settings.put(uid, ps);
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerSettings> e : settings.entrySet()) {
            String k = "players." + e.getKey();
            PlayerSettings ps = e.getValue();
            yml.set(k + ".party-invites", ps.getWhoCanInviteToParty().name());
            yml.set(k + ".challenges", ps.getWhoCanChallenge().name());
            yml.set(k + ".friend-requests", ps.isAcceptFriendRequests());
            yml.set(k + ".status", ps.getStatus().name());
        }
        try {
            file.getParentFile().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save player_settings.yml: " + ex.getMessage());
        }
    }

    public PlayerSettings getSettings(UUID uid) {
        return settings.computeIfAbsent(uid, k -> new PlayerSettings());
    }

    private PlayerSettings.Visibility parseVis(String s) {
        try { return PlayerSettings.Visibility.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return PlayerSettings.Visibility.ALL; }
    }

    private PlayerSettings.Status parseStatus(String s) {
        try { return PlayerSettings.Status.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return PlayerSettings.Status.ONLINE; }
    }
}
```

- [ ] **Step 8.3: Create `SettingsCommand.java` (`/dsettings`)**

```java
package net.rustcore.duel.command;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.settings.PlayerSettings;
import net.rustcore.duel.util.CC;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SettingsCommand implements CommandExecutor {

    private final DuelsPlugin plugin;

    public SettingsCommand(DuelsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CC.parse(plugin.getMessage("only-players")));
            return true;
        }
        if (!player.hasPermission("duels.settings")) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("no-permission"))));
            return true;
        }

        PlayerSettings s = plugin.getSettingsManager().getSettings(player.getUniqueId());

        if (args.length == 0) {
            sendCurrent(player, s);
            return true;
        }

        String key = args[0].toLowerCase();
        if (args.length < 2) {
            player.sendMessage(CC.parse(plugin.getMessage("settings-usage")));
            return true;
        }
        String value = args[1].toUpperCase();

        try {
            switch (key) {
                case "party" -> s.setWhoCanInviteToParty(PlayerSettings.Visibility.valueOf(value));
                case "challenge" -> s.setWhoCanChallenge(PlayerSettings.Visibility.valueOf(value));
                case "friendrequests" -> s.setAcceptFriendRequests(Boolean.parseBoolean(args[1]));
                case "status" -> s.setStatus(PlayerSettings.Status.valueOf(value));
                default -> {
                    player.sendMessage(CC.parse(plugin.getMessage("settings-usage")));
                    return true;
                }
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage(CC.parse(plugin.getMessage("settings-invalid-value")));
            return true;
        }

        plugin.getSettingsManager().save();
        player.sendMessage(CC.parse(plugin.getMessage("settings-updated")));
        sendCurrent(player, s);
        return true;
    }

    private void sendCurrent(Player player, PlayerSettings s) {
        player.sendMessage(CC.parse(plugin.getMessage("settings-header")));
        player.sendMessage(CC.parse(plugin.getMessage("settings-line-party"),
                "{value}", s.getWhoCanInviteToParty().name()));
        player.sendMessage(CC.parse(plugin.getMessage("settings-line-challenge"),
                "{value}", s.getWhoCanChallenge().name()));
        player.sendMessage(CC.parse(plugin.getMessage("settings-line-friendrequests"),
                "{value}", String.valueOf(s.isAcceptFriendRequests())));
        player.sendMessage(CC.parse(plugin.getMessage("settings-line-status"),
                "{value}", s.getStatus().name()));
        player.sendMessage(CC.parse(plugin.getMessage("settings-footer")));
    }
}
```

- [ ] **Step 8.4: Wire into `DuelsPlugin.java`**

Replace the temporary stub from Task 6.7. Add real field:
```java
private SettingsManager settingsManager;
```

In `onEnable()`:
```java
settingsManager = new SettingsManager(this);
settingsManager.load();
```

```java
getCommand("dsettings").setExecutor(new SettingsCommand(this));
```

Replace stub getter with:
```java
public SettingsManager getSettingsManager() { return settingsManager; }
```

In `onDisable()` add `if (settingsManager != null) settingsManager.save();`.

- [ ] **Step 8.5: Wire privacy into `DuelManager.sendRequest`**

In `DuelManager.java` at start of `sendRequest` (line 108) after the two `isInDuel` guards, add:

```java
if (plugin.getSettingsManager() != null) {
    var s = plugin.getSettingsManager().getSettings(target.getUniqueId());
    if (s.getStatus() == PlayerSettings.Status.DO_NOT_DISTURB
            || s.getWhoCanChallenge() == PlayerSettings.Visibility.NOBODY) {
        sender.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("challenge-blocked"), "{player}", target.getName())));
        return;
    }
    if (s.getWhoCanChallenge() == PlayerSettings.Visibility.FRIENDS_ONLY
            && !plugin.getFriendManager().isFriend(sender.getUniqueId(), target.getUniqueId())) {
        sender.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("challenge-blocked"), "{player}", target.getName())));
        return;
    }
}
```

Add the import `import net.rustcore.duel.settings.PlayerSettings;` at the top of `DuelManager.java`.

- [ ] **Step 8.6: Update `plugin.yml`**

Commands:
```yaml
  dsettings:
    description: Duel privacy settings
    usage: /<command> [party|challenge|friendrequests|status] [value]
```

Permissions:
```yaml
  duels.settings:
    description: Edit duel privacy settings
    default: true
```

- [ ] **Step 8.7: Add messages to `config.yml`**

Under `messages:` append:
```yaml
  settings-usage: "<gray>Usage: <white>/dsettings <party|challenge|friendrequests|status> <ALL|FRIENDS_ONLY|NOBODY | true|false | ONLINE|OFFLINE|DO_NOT_DISTURB>"
  settings-invalid-value: "<red>Invalid value for that setting."
  settings-updated: "<green>Setting updated."
  settings-header: "<gold>Your duel privacy settings:"
  settings-line-party: "  <yellow>Party invites: <white><value>"
  settings-line-challenge: "  <yellow>Challenges: <white><value>"
  settings-line-friendrequests: "  <yellow>Friend requests: <white><value>"
  settings-line-status: "  <yellow>Status: <white><value>"
  settings-footer: "<gray>Change with /dsettings <key> <value>"
  challenge-blocked: "<red><player> <red>is not accepting duel challenges."
```

- [ ] **Step 8.8: Verify compile**

Run: `./gradlew compileJava` — expect BUILD SUCCESSFUL.

- [ ] **Step 8.9: Commit**

```bash
git add -A
git commit -m "feat: add player privacy settings and /dsettings command; wire into challenges and party invites"
```

---

## TASK 9 — Fixed-kit mode foundation (e.g. NoDebuff)

**Files:**
- Create: `src/main/java/net/rustcore/duel/mode/impl/FixedKitMode.java`
- Modify: `src/main/java/net/rustcore/duel/mode/ModeManager.java`
- Create: `src/main/resources/modes/nodebuff.yml`
- Modify (maybe): `src/main/java/net/rustcore/duel/kit/KitItemParser.java` — verify `SPLASH_POTION:EFFECT:LEVEL` parse

- [ ] **Step 9.1: Check KitItemParser capabilities before writing FixedKitMode**

Open `src/main/java/net/rustcore/duel/kit/KitItemParser.java` (Read tool). Goal: does `parseItemId` accept a string like `SPLASH_POTION:INSTANT_HEAL:2`?

If **yes** — skip 9.2.

If **no** — add a branch in `parseVanillaItem` that detects a `:` in the material name, splits `material:effect:level` (or `material:effect:level:duration` for lingering), builds the `ItemStack` with `PotionMeta` using `PotionEffectType.getByName(effect)` and `new PotionEffect(type, durationTicks, amplifier-1)`. Keep backward compatibility — plain material names still work.

- [ ] **Step 9.2: Write `FixedKitMode.java`**

```java
package net.rustcore.duel.mode.impl;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.duel.Duel;
import net.rustcore.duel.kit.KitItemParser;
import net.rustcore.duel.kit.KitLayout;
import net.rustcore.duel.mode.DuelMode;
import net.rustcore.duel.modification.Modification;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;

public class FixedKitMode implements DuelMode {

    private final DuelsPlugin plugin;
    private final String id;
    private final String displayName;
    private final String description;
    private final Material icon;
    private final boolean enabled;
    private final int defaultBestOf;
    private final List<Integer> availableBestOf;

    private final ItemStack helmet;
    private final ItemStack chestplate;
    private final ItemStack leggings;
    private final ItemStack boots;
    private final Map<Integer, ItemStack> hotbar; // slot -> item

    public FixedKitMode(DuelsPlugin plugin, String id, ConfigurationSection cfg) {
        this.plugin = plugin;
        this.id = id;
        this.displayName = cfg.getString("display-name", id);
        this.description = cfg.getString("description", "");
        this.icon = Material.valueOf(cfg.getString("icon", "DIAMOND_SWORD").toUpperCase());
        this.enabled = cfg.getBoolean("enabled", true);
        this.defaultBestOf = cfg.getInt("default-best-of", 1);
        this.availableBestOf = cfg.getIntegerList("available-best-of");

        ConfigurationSection kit = cfg.getConfigurationSection("fixed-kit");
        if (kit == null) throw new IllegalArgumentException("fixed-kit section missing in mode " + id);

        this.helmet = parseSimple(kit.getString("helmet"));
        this.chestplate = parseSimple(kit.getString("chestplate"));
        this.leggings = parseSimple(kit.getString("leggings"));
        this.boots = parseSimple(kit.getString("boots"));
        this.hotbar = new LinkedHashMap<>();
        ConfigurationSection hotbarCfg = kit.getConfigurationSection("hotbar");
        if (hotbarCfg != null) {
            for (String slotKey : hotbarCfg.getKeys(false)) {
                int slot;
                try { slot = Integer.parseInt(slotKey); } catch (NumberFormatException e) { continue; }
                ItemStack stack = parseSimple(hotbarCfg.getString(slotKey));
                if (stack != null) hotbar.put(slot, stack);
            }
        }
    }

    private ItemStack parseSimple(String raw) {
        if (raw == null || raw.isBlank()) return null;
        // Use existing parser: raw may be "DIAMOND_SWORD" or "SPLASH_POTION:INSTANT_HEAL:2"
        return KitItemParser.parseItemId(raw, 1);
    }

    @Override public String getId() { return id; }
    @Override public String getDisplayName() { return displayName; }
    @Override public String getDescription() { return description; }
    @Override public Material getIcon() { return icon; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public List<Integer> getAvailableBestOf() {
        return availableBestOf.isEmpty() ? List.of(1, 3, 5) : availableBestOf;
    }
    @Override public int getDefaultBestOf() { return defaultBestOf; }
    @Override public boolean isRedraftEachRound() { return false; }
    @Override public Modification getModification() { return null; }

    @Override
    public void onRoundSetup(Duel duel, Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
    }

    @Override
    public void onPlayerReady(Duel duel, Player player) { /* auto-ready */ }

    @Override
    public void onRoundStart(Duel duel) {
        for (Player p : duel.getPlayers()) {
            giveKit(p);
        }
    }

    private void giveKit(Player p) {
        PlayerInventory inv = p.getInventory();
        if (helmet != null) inv.setHelmet(helmet.clone());
        if (chestplate != null) inv.setChestplate(chestplate.clone());
        if (leggings != null) inv.setLeggings(leggings.clone());
        if (boots != null) inv.setBoots(boots.clone());

        KitLayout layout = plugin.getKitLayoutManager() != null
                ? plugin.getKitLayoutManager().getLayout(p.getUniqueId(), id)
                : null;

        for (Map.Entry<Integer, ItemStack> entry : hotbar.entrySet()) {
            int origSlot = entry.getKey();
            int targetSlot = layout != null ? layout.remapSlot(origSlot) : origSlot;
            inv.setItem(targetSlot, entry.getValue().clone());
        }
    }

    @Override
    public boolean onPlayerDeath(Duel duel, Player dead, Player killer) {
        return true;
    }

    @Override
    public void onDuelEnd(Duel duel) { /* nothing */ }

    @Override
    public void reload() { /* reloaded via ModeManager */ }
}
```

- [ ] **Step 9.3: Update `ModeManager.java` to dispatch by `type`**

In `ModeManager.load()` (line 22), after the hardcoded `KitBuilderMode` construction, replace with a config-driven loader. Show the full replacement of `load()`:

```java
public void load() {
    modes.clear();

    File modesDir = new File(plugin.getDataFolder(), "modes");
    if (!modesDir.exists()) {
        modesDir.mkdirs();
        // Also seed defaults from resources if any (nodebuff.yml, etc.)
    }

    // Legacy default kitbuilder for back-compat
    KitBuilderMode kitBuilder = new KitBuilderMode(plugin);
    kitBuilder.load();
    modes.put(kitBuilder.getId(), kitBuilder);

    File[] files = modesDir.listFiles((d, n) -> n.endsWith(".yml"));
    if (files != null) {
        for (File f : files) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
            String id = yml.getString("id", f.getName().replace(".yml", ""));
            String type = yml.getString("type", "kitbuilder").toLowerCase();
            try {
                switch (type) {
                    case "fixed" -> {
                        FixedKitMode m = new FixedKitMode(plugin, id, yml);
                        modes.put(m.getId(), m);
                    }
                    case "kitbuilder" -> {
                        // already registered as default for now; per-file kitbuilder variants not supported yet
                    }
                    default -> plugin.getLogger().warning("Unknown mode type '" + type + "' in " + f.getName());
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to load mode " + id + ": " + ex.getMessage());
            }
        }
    }
}
```

Add imports as needed (`java.io.File`, `org.bukkit.configuration.file.YamlConfiguration`, `net.rustcore.duel.mode.impl.FixedKitMode`).

- [ ] **Step 9.4: Create `modes/nodebuff.yml`**

Write to `src/main/resources/modes/nodebuff.yml`:

```yaml
id: nodebuff
display-name: "<red>NoDebuff"
description: "Pure PvP with potions"
icon: SPLASH_POTION
enabled: true
type: fixed
default-best-of: 1
available-best-of: [1, 3, 5]
fixed-kit:
  helmet: DIAMOND_HELMET
  chestplate: DIAMOND_CHESTPLATE
  leggings: DIAMOND_LEGGINGS
  boots: DIAMOND_BOOTS
  hotbar:
    0: DIAMOND_SWORD
    1: SPLASH_POTION:INSTANT_HEAL:2
    2: SPLASH_POTION:INSTANT_HEAL:2
    3: SPLASH_POTION:INSTANT_HEAL:2
    4: SPLASH_POTION:INSTANT_HEAL:2
    5: SPLASH_POTION:INSTANT_HEAL:2
    6: SPLASH_POTION:INSTANT_HEAL:2
    7: GOLDEN_APPLE
    8: WATER_BUCKET
```

- [ ] **Step 9.5: Verify compile**

Run: `./gradlew compileJava` — expect BUILD SUCCESSFUL. If `KitLayoutManager` is not yet compiled (it arrives in Task 10), add a temporary stub getter in DuelsPlugin that returns `null`:
```java
public net.rustcore.duel.kit.KitLayoutManager getKitLayoutManager() { return null; }
```

- [ ] **Step 9.6: Commit**

```bash
git add -A
git commit -m "feat: add FixedKitMode (e.g. NoDebuff) loaded from modes/*.yml via 'type: fixed'"
```

---

## TASK 10 — Per-player item layout for fixed-kit modes

**Files:**
- Create: `src/main/java/net/rustcore/duel/kit/KitLayout.java`
- Create: `src/main/java/net/rustcore/duel/kit/KitLayoutManager.java`
- Create: `src/main/java/net/rustcore/duel/command/KitLayoutCommand.java`
- Create: `src/main/java/net/rustcore/duel/listener/KitLayoutEditorListener.java`
- Modify: `src/main/java/net/rustcore/duel/DuelsPlugin.java` — register listener + command, replace stub
- Modify: `src/main/resources/plugin.yml` — command + permission
- Modify: `src/main/resources/config.yml` — messages

- [ ] **Step 10.1: Create `KitLayout.java`**

```java
package net.rustcore.duel.kit;

import java.util.HashMap;
import java.util.Map;

public class KitLayout {

    // originalSlot -> customSlot
    private final Map<Integer, Integer> slotRemap = new HashMap<>();

    public int remapSlot(int original) {
        return slotRemap.getOrDefault(original, original);
    }

    public void setRemap(int original, int custom) {
        slotRemap.put(original, custom);
    }

    public void clear() { slotRemap.clear(); }

    public Map<Integer, Integer> getRaw() { return slotRemap; }
}
```

- [ ] **Step 10.2: Create `KitLayoutManager.java`**

```java
package net.rustcore.duel.kit;

import net.rustcore.duel.DuelsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class KitLayoutManager {

    private final DuelsPlugin plugin;
    // playerId -> (modeId -> layout)
    private final Map<UUID, Map<String, KitLayout>> layouts = new ConcurrentHashMap<>();
    private final File file;

    public KitLayoutManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/kit_layouts.yml");
    }

    public void load() {
        layouts.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("players");
        if (root == null) return;
        for (String uidKey : root.getKeys(false)) {
            UUID uid;
            try { uid = UUID.fromString(uidKey); } catch (IllegalArgumentException e) { continue; }
            Map<String, KitLayout> perMode = new HashMap<>();
            ConfigurationSection modesSec = root.getConfigurationSection(uidKey);
            if (modesSec == null) continue;
            for (String modeId : modesSec.getKeys(false)) {
                KitLayout layout = new KitLayout();
                ConfigurationSection remapSec = modesSec.getConfigurationSection(modeId);
                if (remapSec != null) {
                    for (String origKey : remapSec.getKeys(false)) {
                        try {
                            layout.setRemap(Integer.parseInt(origKey), remapSec.getInt(origKey));
                        } catch (NumberFormatException ignored) {}
                    }
                }
                perMode.put(modeId.toLowerCase(), layout);
            }
            layouts.put(uid, perMode);
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, KitLayout>> e : layouts.entrySet()) {
            for (Map.Entry<String, KitLayout> m : e.getValue().entrySet()) {
                String prefix = "players." + e.getKey() + "." + m.getKey();
                for (Map.Entry<Integer, Integer> r : m.getValue().getRaw().entrySet()) {
                    yml.set(prefix + "." + r.getKey(), r.getValue());
                }
            }
        }
        try {
            file.getParentFile().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save kit_layouts.yml: " + ex.getMessage());
        }
    }

    public KitLayout getLayout(UUID uid, String modeId) {
        Map<String, KitLayout> perMode = layouts.get(uid);
        if (perMode == null) return null;
        return perMode.get(modeId.toLowerCase());
    }

    public void setLayout(UUID uid, String modeId, KitLayout layout) {
        layouts.computeIfAbsent(uid, k -> new HashMap<>()).put(modeId.toLowerCase(), layout);
        save();
    }

    public void reset(UUID uid, String modeId) {
        Map<String, KitLayout> perMode = layouts.get(uid);
        if (perMode != null) perMode.remove(modeId.toLowerCase());
        save();
    }
}
```

- [ ] **Step 10.3: Create `KitLayoutCommand.java`**

```java
package net.rustcore.duel.command;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.kit.KitLayout;
import net.rustcore.duel.mode.DuelMode;
import net.rustcore.duel.mode.impl.FixedKitMode;
import net.rustcore.duel.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class KitLayoutCommand implements CommandExecutor {

    public static final String EDITOR_KEY_META = "duels.kitlayout.editor.mode";
    public static final String EDITOR_TITLE_PREFIX = "Kit Layout: ";

    private final DuelsPlugin plugin;

    public KitLayoutCommand(DuelsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CC.parse(plugin.getMessage("only-players")));
            return true;
        }
        if (!player.hasPermission("duels.kitlayout")) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("no-permission"))));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(CC.parse(plugin.getMessage("kitlayout-usage")));
            return true;
        }

        String sub = args[0].toLowerCase();
        if (args.length < 2) {
            player.sendMessage(CC.parse(plugin.getMessage("kitlayout-usage")));
            return true;
        }
        String modeId = args[1].toLowerCase();
        DuelMode mode = plugin.getModeManager().getMode(modeId);
        if (!(mode instanceof FixedKitMode fk)) {
            player.sendMessage(CC.parse(plugin.getMessage("kitlayout-not-fixed"),
                    "{mode}", modeId));
            return true;
        }

        switch (sub) {
            case "edit" -> openEditor(player, fk);
            case "reset" -> {
                plugin.getKitLayoutManager().reset(player.getUniqueId(), modeId);
                player.sendMessage(CC.parse(plugin.getMessage("kitlayout-reset")));
            }
            case "show" -> showLayout(player, modeId);
            default -> player.sendMessage(CC.parse(plugin.getMessage("kitlayout-usage")));
        }
        return true;
    }

    private void openEditor(Player player, FixedKitMode mode) {
        Inventory inv = Bukkit.createInventory(player, InventoryType.HOPPER, EDITOR_TITLE_PREFIX + mode.getId());
        // Hopper is 5 slots — not enough. Use a 9-slot chest-style inventory instead:
        inv = Bukkit.createInventory(player, 9, EDITOR_TITLE_PREFIX + mode.getId());

        // Populate with the default-ordered hotbar items
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = mode.getHotbarItem(slot);
            if (item != null) inv.setItem(slot, item.clone());
        }
        player.setMetadata(EDITOR_KEY_META, new org.bukkit.metadata.FixedMetadataValue(plugin, mode.getId()));
        player.openInventory(inv);
    }

    private void showLayout(Player player, String modeId) {
        KitLayout layout = plugin.getKitLayoutManager().getLayout(player.getUniqueId(), modeId);
        if (layout == null || layout.getRaw().isEmpty()) {
            player.sendMessage(CC.parse(plugin.getMessage("kitlayout-default")));
            return;
        }
        player.sendMessage(CC.parse(plugin.getMessage("kitlayout-header"), "{mode}", modeId));
        for (var e : layout.getRaw().entrySet()) {
            player.sendMessage("  §7" + e.getKey() + " §8-> §f" + e.getValue());
        }
    }
}
```

Note: `FixedKitMode` needs a helper. Add in `FixedKitMode.java`:
```java
public ItemStack getHotbarItem(int slot) { return hotbar.get(slot); }
```

- [ ] **Step 10.4: Create `KitLayoutEditorListener.java`**

```java
package net.rustcore.duel.listener;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.command.KitLayoutCommand;
import net.rustcore.duel.kit.KitLayout;
import net.rustcore.duel.mode.DuelMode;
import net.rustcore.duel.mode.impl.FixedKitMode;
import net.rustcore.duel.util.CC;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class KitLayoutEditorListener implements Listener {

    private final DuelsPlugin plugin;

    public KitLayoutEditorListener(DuelsPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        if (title == null || !title.startsWith(KitLayoutCommand.EDITOR_TITLE_PREFIX)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        String modeId = title.substring(KitLayoutCommand.EDITOR_TITLE_PREFIX.length()).toLowerCase();
        DuelMode mode = plugin.getModeManager().getMode(modeId);
        if (!(mode instanceof FixedKitMode fk)) return;

        // Build new layout: for each original hotbar slot, find where its item now lives
        Inventory inv = event.getInventory();
        KitLayout layout = new KitLayout();
        for (int origSlot = 0; origSlot < 9; origSlot++) {
            ItemStack expected = fk.getHotbarItem(origSlot);
            if (expected == null) continue;
            for (int newSlot = 0; newSlot < inv.getSize(); newSlot++) {
                ItemStack here = inv.getItem(newSlot);
                if (here != null && here.isSimilar(expected)) {
                    layout.setRemap(origSlot, newSlot);
                    inv.setItem(newSlot, null); // avoid re-matching
                    break;
                }
            }
        }
        plugin.getKitLayoutManager().setLayout(player.getUniqueId(), modeId, layout);
        player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("kitlayout-saved"), "{mode}", modeId)));

        if (player.hasMetadata(KitLayoutCommand.EDITOR_KEY_META)) {
            player.removeMetadata(KitLayoutCommand.EDITOR_KEY_META, plugin);
        }
    }
}
```

- [ ] **Step 10.5: Register in `DuelsPlugin.java`**

Field: `private KitLayoutManager kitLayoutManager;`

In `onEnable()`:
```java
kitLayoutManager = new KitLayoutManager(this);
kitLayoutManager.load();
```

```java
getCommand("kitlayout").setExecutor(new KitLayoutCommand(this));
getServer().getPluginManager().registerEvents(new KitLayoutEditorListener(this), this);
```

Replace the stub from Task 9.5 with the real getter:
```java
public KitLayoutManager getKitLayoutManager() { return kitLayoutManager; }
```

In `onDisable()` add `if (kitLayoutManager != null) kitLayoutManager.save();`.

- [ ] **Step 10.6: Update `plugin.yml`**

Commands:
```yaml
  kitlayout:
    description: Customize per-mode fixed-kit hotbar layout
    usage: /<command> <edit|reset|show> <mode>
    aliases: [ kl ]
```

Permissions:
```yaml
  duels.kitlayout:
    description: Edit kit layouts for fixed-kit modes
    default: true
```

- [ ] **Step 10.7: Add messages to `config.yml`**

Under `messages:` append:
```yaml
  kitlayout-usage: "<gray>Usage: <white>/kitlayout <edit|reset|show> <mode>"
  kitlayout-not-fixed: "<red>Mode <white><mode> <red>does not support custom layouts (fixed-kit modes only)."
  kitlayout-default: "<gray>Using default layout."
  kitlayout-header: "<gold>Layout for <mode>:"
  kitlayout-reset: "<green>Layout reset."
  kitlayout-saved: "<green>Saved new layout for <white><mode><green>."
```

- [ ] **Step 10.8: Verify compile**

Run: `./gradlew compileJava` — expect BUILD SUCCESSFUL.

- [ ] **Step 10.9: Commit**

```bash
git add -A
git commit -m "feat: add per-player kit layout editor for fixed-kit modes (/kitlayout edit|reset|show)"
```

---

## Final verification

- [ ] **Step F.1: Full build + plugin packaging**

Run: `./gradlew build` — expect BUILD SUCCESSFUL and a shaded jar in `build/libs/`.

- [ ] **Step F.2: Smoke-test checklist on a dev Paper server**

1. `/hub` teleports to hub #1 on this server.
2. `/hub 2` — "Hub 2 doesn't exist (max 1)" (unless 2+ hubs configured).
3. `/lobby` — BungeeCord connect attempt to `lobby.server`.
4. `/duel queue kitbuilder` + check `%duels_queue_size%` ≥ 1, `%duels_queue_size_kitbuilder%` ≥ 1, `%duels_queue_ranked%` = `UNRANKED`.
5. `/duel ranked` toggle → `%duels_queue_ranked%` flips.
6. `/duel challenge <player>` — accept, finish a duel → stats do NOT update (forced-unranked).
7. `/f add <player>`, other player `/f accept` — both receive "now friends".
8. `/party invite`, `/party accept`, `/party list`.
9. `/dsettings challenge NOBODY` — others cannot `/duel challenge` me.
10. `/duel queue nodebuff` → round starts with diamond armor + 7 pots + gap + water bucket.
11. `/kitlayout edit nodebuff` — rearrange, close → layout saved. Next duel uses new slots.

- [ ] **Step F.3: No final commit needed unless fixups emerge from smoke-test.**

---

## Spec Coverage Self-Review Notes

- TASK 1 (hub/lobby swap + `/hub [n]`) — ✅ Task 1.
- TASK 2 (permissions) — ✅ Task 2.
- TASK 3 (fix `%duels_queue_size%`) — ✅ Task 3.
- TASK 4 (`%duels_queue_ranked%`) — ✅ Task 4.
- TASK 5 (force unranked on challenge) — ✅ Task 5; note current ELO gate already protects by accident but we add an explicit flag for durability.
- TASK 6 (friend system) — ✅ Task 6.
- TASK 7 (party commands — queue integration deferred) — ✅ Task 7, queue integration deliberately out of scope per original spec ("deferred to future team-modes task").
- TASK 8 (privacy settings) — ✅ Task 8.
- TASK 9 (fixed-kit mode) — ✅ Task 9.
- TASK 10 (per-player layout) — ✅ Task 10.

No placeholders, no "TBD", no "similar to X" — every code step shows the code. Method names are consistent across tasks (`sendToHub`, `getHubSpawn`, `getFriendManager`, `getPartyManager`, `getSettingsManager`, `getKitLayoutManager`, `FixedKitMode.getHotbarItem`).
