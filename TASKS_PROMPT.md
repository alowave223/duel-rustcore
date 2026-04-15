# Autonomous Implementation Prompt — RustCore-Duels

## Project Context

Paper/Spigot Minecraft plugin. Java 21, Paper API 1.21.11. Working directory: `c:\Users\alowave\Documents\duel-rustcore`.

Key files already read and understood:
- `DuelsPlugin.java` — plugin main, registers commands `duel` and `hub`
- `DuelCommand.java` — handles queue/challenge/accept/decline/stats/ranked/reload/forcestart
- `HubCommand.java` — sends player to BungeeCord server from `hub.server` config key
- `DuelManager.java` — queue pool uses `modeId + ":ranked"` / `":unranked"` suffix; `rankedPreference` map; `sendRequest()` does NOT force unranked
- `DuelQueue.java` — per-mode FIFO queue; `getQueueSize(modeId)` returns size of one key
- `LobbyManager.java` — single `lobbySpawn` from `general.lobby-world` + `general.lobby-spawn.*`; `sendToLobby()` teleports + gives hotbar items
- `DuelsExpansion.java` — `%duels_queue_size%` sums `queue.getQueueSize(m.getId())` per mode — **BUG**: keys in queue are `modeId:ranked`/`modeId:unranked`, not plain `modeId`; `getQueueSize("kitbuilder")` always returns 0
- `plugin.yml` — commands: `duel` (aliases: duels), `hub` (aliases: lobby, spawn); permissions: `duels.admin` (op), `duels.play` (true), `duels.queue` (true)

## Rules for All Tasks
- Do NOT add unnecessary comments, docstrings, or abstractions.
- Do NOT refactor code not touched by the task.
- Use existing patterns: `CC.parse(...)`, `plugin.getMessage(...)`, `plugin.getConfig()`.
- After each task, compile mentally (verify imports, types) before moving to next.
- One commit per logical task group using `git commit`.
- Work sequentially — each task may depend on the previous.

---

## TASK 1 — Rename hub↔lobby terminology + `/hub <number>` argument

### What to change:
**Concept swap:** "hub" = spawn on the duels server (where players wait between duels). "lobby" = the main network lobby server (BungeeCord destination).

1. **`HubCommand.java`** — currently named HubCommand but sends to BungeeCord `hub.server`. Rename the BungeeCord destination config key to `lobby.server`. Keep class name as-is.
2. **`LobbyManager.java`** — rename method `sendToLobby()` → `sendToHub()`, field `lobbySpawn` → `hubSpawn`, config keys `general.lobby-world` → `general.hub-world`, `general.lobby-spawn.*` → `general.hub-spawn.*`. Update all internal references.
3. **`plugin.yml`** — command `hub` keeps aliases `[lobby, spawn]`. Add new command `lobby` that connects to BungeeCord (the old `/hub` BungeeCord behavior). Update descriptions.
4. **`DuelsPlugin.java`** — register new `LobbyCommand` for the `lobby` command; update `sendToLobby` → `sendToHub` calls.
5. **Create `LobbyCommand.java`** in `command/` — identical to old HubCommand BungeeCord logic, uses config key `lobby.server`. Does NOT forfeit duel (player is leaving the duel server entirely, forfeit already happens via disconnect).
6. **`HubCommand.java`** — add `<number>` argument: `/hub [number]`. Config structure:
   ```yaml
   hubs:
     - world: "hub_1"
       x: 0.5
       y: 64.0
       z: 0.5
       yaw: 0.0
       pitch: 0.0
     - world: "hub_2"
       ...
   ```
   If no number given, use index 0 (first hub). If number out of range, tell player "Hub <n> doesn't exist". Parse from `LobbyManager` which should expose `getHubSpawn(int index)`.
7. **`LobbyManager.java`** — replace single `hubSpawn` with `List<Location> hubSpawns`. Load from `hubs` list in config. `sendToHub(Player, int index)` overload + `sendToHub(Player)` uses index 0. Update `loadLobbySpawn()` → `loadHubSpawns()`.
8. **`config.yml`** — update default config keys accordingly. Add `lobby.server: "lobby"` and `hubs:` list with one default entry.
9. All callers of `sendToLobby(player)` → `sendToHub(player)` (check DuelListener, DuelManager, etc. with Grep).

---

## TASK 2 — Permissions for all commands

Current state: only `duels.queue` and `duels.admin` exist. Most subcommands have no permission guard.

### Changes:
1. **`plugin.yml`** — add permissions:
   - `duels.hub` — use `/hub`; default: true
   - `duels.lobby` — use `/lobby`; default: true
   - `duels.challenge` — challenge players; default: true
   - `duels.stats` — view stats; default: true
   - `duels.ranked` — toggle ranked mode; default: true
   - `duels.draftmenu` — open kit draft menu; default: true
   - Keep `duels.queue`, `duels.admin`, `duels.play`

2. **`HubCommand.java`** — check `player.hasPermission("duels.hub")` at top.
3. **`LobbyCommand.java`** — check `player.hasPermission("duels.lobby")`.
4. **`DuelCommand.java`**:
   - `handleChallenge` — already checks `duels.queue`, change to `duels.challenge`
   - `handleStats` — add `duels.stats` check
   - `handleRankedToggle` — add `duels.ranked` check
   - `handleOpenKitMenu` — add `duels.draftmenu` check
   - `handleAccept` / `handleDecline` — add `duels.challenge` check
   - `handleLeave` — add `duels.play` check
   - `handleQueue` — keep `duels.queue`

---

## TASK 3 — Fix `%duels_queue_size%` and `%duels_queue_size_<mode>%`

### Root cause:
`DuelManager.queuePlayer()` stores players under keys like `"kitbuilder:ranked"` and `"kitbuilder:unranked"`. But `DuelsExpansion.resolveQueue()` calls `queue.getQueueSize(m.getId())` with plain `"kitbuilder"` — always 0.

### Fix in `DuelsExpansion.java` — `resolveQueue()`:
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

No other changes needed. `DuelQueue.getQueueSize()` already works correctly with any key.

---

## TASK 4 — Add `%duels_queue_ranked%` placeholder

New placeholder showing the player's current ranked search status.

### In `DuelsExpansion.java` — `resolveQueue()`:
Add case before the `return EMPTY` at bottom:
```java
if (rest.equalsIgnoreCase("ranked")) {
    boolean ranked = plugin.getDuelManager().isRanked(player.getUniqueId());
    return ranked ? "RANKED" : "UNRANKED";
}
```

Update the javadoc comment at top of class to document `%duels_queue_ranked%`.

---

## TASK 5 — Force unranked on `/duel challenge`

### In `DuelManager.java` — `sendRequest()`:
The created duel via challenge should always be unranked. The method signature is fine. After accepting, `createDuel()` is called. The issue is that ELO changes on duel end respect the ranked flag.

Check `Duel.java` to find where ELO is updated (look for `StatsManager` calls with `ranked` check). If no such check exists:
1. Add field `boolean ranked` to `DuelRequest` record.
2. In `sendRequest()`, set `ranked = false` always for challenges.
3. Pass it through `acceptRequest()` → `createDuel()`.
4. Add `boolean ranked` param to `createDuel()` or store it in `Duel`.
5. In `Duel.java` end-of-round / end-of-duel logic, skip ELO update if `!duel.isRanked()`.

If `Duel.java` already has a ranked field or ELO is only updated when `DuelManager.isRanked()` — adapt accordingly after reading `Duel.java` first.

**Read `Duel.java` and `StatsManager.java` before implementing this task.**

---

## TASK 6 — Friend system + `/f` command

### New files to create:

**`friend/FriendManager.java`**:
```java
// Persistent friend lists per player.
// Storage: data/friends.yml — structure: players.<uuid>.friends: [uuid, uuid, ...]
// Methods:
//   addFriend(UUID requester, UUID target) — adds to both lists
//   removeFriend(UUID remover, UUID target) — removes from both lists
//   getFriends(UUID player) → Set<UUID>
//   isFriend(UUID a, UUID b) → boolean
//   sendRequest(UUID from, UUID to) — stores pending request, expires 60s
//   acceptRequest(UUID acceptor) — acceptor accepts request from whoever sent to them
//   declineRequest(UUID decliner)
//   hasPendingRequest(UUID player) → boolean (has incoming)
//   load() / save()
```

**`command/FriendCommand.java`**:
```
/f — open friend menu (DeluxeMenus or simple chat list for now — use chat list)
/f add <name> — send friend request
/f remove <name> — remove friend
/f accept — accept pending request  
/f decline — decline pending request
/f list — list friends with online status
```
Permission: `duels.friends` (default: true). Add to plugin.yml.

Register in `DuelsPlugin.java`: `getCommand("f").setExecutor(new FriendCommand(this));`

Add to `plugin.yml` commands section:
```yaml
f:
  description: Friend system
  usage: /<command> [add|remove|list|accept|decline] [player]
  aliases: [friend, friends]
```

Add `friendManager` field to `DuelsPlugin`, expose via `getFriendManager()`.

### Messages to add to config.yml `messages:` section:
```yaml
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
```

---

## TASK 7 — Party/Group system (2–5 players, unranked only)

### New files:

**`party/Party.java`**:
```java
// Fields: UUID leaderId, List<UUID> members (max 5), UUID partyId
// Methods: addMember, removeMember, isMember, isLeader, getSize, isFull
// Max size enforced: 2–5
```

**`party/PartyManager.java`**:
```java
// Storage: in-memory only (parties don't persist restarts)
// Map<UUID, Party> playerParty — player -> their party
// Map<UUID, UUID> invites — invitee -> inviter (expires 60s)
// Methods:
//   createParty(UUID leader) → Party
//   disbandParty(UUID leader)
//   invite(UUID leader, UUID target) — leader sends invite
//   acceptInvite(UUID acceptor)
//   declineInvite(UUID decliner)
//   leaveParty(UUID member)
//   kickMember(UUID leader, UUID target)
//   getParty(UUID player) → Party or null
//   isInParty(UUID player) → boolean
```

**`command/PartyCommand.java`**:
```
/party — show party info
/party invite <name> — leader invites player
/party accept — accept invite
/party decline — decline invite  
/party leave — leave party
/party kick <name> — leader kicks member
/party disband — leader disbands party
/party list — list members
```
Alias: `/p`. Permission: `duels.party` (default: true).

Register in `DuelsPlugin.java`. Add command to `plugin.yml`.

### Integration with queue:
When a party queues: all members queue together, match only when both parties are found OR party vs solo (future). For now: parties can queue but only match against another party of same size OR wait — implement simple: all party members enter queue simultaneously with same `partyGroupId` tag. When match is found, it's a group duel. Do NOT implement ranked for party duels — always unranked.

Party queue integration is complex — **scope for this task**: implement Party system + commands only. Queue integration is deferred to TASK 9 (team modes).

---

## TASK 8 — Player privacy settings

### New class: `settings/PlayerSettings.java`
```java
public class PlayerSettings {
    public enum Visibility { ALL, FRIENDS_ONLY, NOBODY }
    public enum Status { ONLINE, OFFLINE, DO_NOT_DISTURB }
    
    private Visibility whoCanInviteToParty = Visibility.ALL;
    private Visibility whoCanChallenge = Visibility.ALL;
    private boolean acceptFriendRequests = true;
    private Status status = Status.ONLINE;
    
    // getters + setters
}
```

### New class: `settings/SettingsManager.java`
```java
// Map<UUID, PlayerSettings> settings
// Storage: data/player_settings.yml
// Methods: getSettings(UUID), load(), save()
```

### Integration:
1. **`DuelManager.sendRequest()`** — before sending request, check target's `PlayerSettings`:
   - If `status == DO_NOT_DISTURB` → send "Player is not accepting duels." to sender, abort.
   - If `whoCanChallenge == NOBODY` → same.
   - If `whoCanChallenge == FRIENDS_ONLY` → check `FriendManager.isFriend(sender, target)`, if not → abort.

2. **`PartyManager.invite()`** — before sending invite, check target's settings:
   - If `status == DO_NOT_DISTURB` → abort silently (no message to sender per DND behavior).
   - If `whoCanInviteToParty == NOBODY` → abort.
   - If `whoCanInviteToParty == FRIENDS_ONLY` → check friendship.

3. **`FriendManager.sendRequest()`** — check `acceptFriendRequests == false` → abort.

4. **When `status == DO_NOT_DISTURB`**: suppress all incoming messages from non-system sources (challenge requests, party invites, friend requests). You do NOT need to filter chat messages.

5. **New command or subcommand** — `/duel settings` or separate `/dsettings` command to toggle these. Use simple chat-based menu (send clickable text via `CC.parse` with `[click]` tags if supported, otherwise just show current values and `/duel settings <key> <value>` subcommand). Permission: `duels.settings` (default: true).

Add `settingsManager` to `DuelsPlugin`. Expose via `getSettingsManager()`.

---

## TASK 9 — Fixed-kit mode foundation

### Goal: enable modes where the kit (items) is predefined in config, not draft-picked.

### Existing code review needed:
Read `mode/DuelMode.java` and `mode/impl/KitBuilderMode.java` and `kit/KitItemParser.java` first to understand how kits work now.

### New mode type: `FixedKitMode.java` in `mode/impl/`:
```java
public class FixedKitMode extends DuelMode {
    // Kit defined in mode config under `fixed-kit:` section
    // On round start: give items directly, no drafting phase
    // No DRAFTING state — goes straight PREPARING → COUNTDOWN → ACTIVE
}
```

Config structure for a fixed-kit mode (e.g. `modes/nodebuff.yml`):
```yaml
id: nodebuff
display-name: "NoDebuff"
type: fixed  # new field — "fixed" vs "kitbuilder" (existing)
default-best-of: 1
available-best-of: [1, 3, 5]
arena-tags: []
fixed-kit:
  helmet: DIAMOND_HELMET
  chestplate: DIAMOND_CHESTPLATE
  leggings: DIAMOND_LEGGINGS
  boots: DIAMOND_BOOTS
  hotbar:
    0: DIAMOND_SWORD
    1: SPLASH_POTION:INSTANT_HEAL:2  # material:effect:level format
    2: SPLASH_POTION:INSTANT_HEAL:2
    # ...
```

### `KitItemParser.java` — extend to parse `SPLASH_POTION:EFFECT:LEVEL` format if not already supported.

### `ModeManager.java` — when loading a mode config, check `type` field:
- `"fixed"` → instantiate `FixedKitMode`
- `"kitbuilder"` or absent → instantiate existing `KitBuilderMode`

### Integration with `Duel.java`:
`FixedKitMode` overrides `onRoundStart(Duel duel)` to give kit items directly. `KitBuilderMode` keeps draft flow. This requires `DuelMode` to have abstract/overridable `onRoundStart()` — check if this hook exists, add if not.

---

## TASK 10 — Item layout customization for fixed-kit modes

### Goal: players can rearrange items within their hotbar/inventory for fixed-kit modes.

### New class: `kit/KitLayout.java`
```java
// Stores custom slot mapping for a player in a specific mode
// Map<Integer, Integer> slotRemap — originalSlot -> customSlot
// Methods: remap(ItemStack[] originalKit) → ItemStack[] rearranged
```

### New class: `kit/KitLayoutManager.java`
```java
// Map<UUID, Map<String, KitLayout>> layouts — player -> modeId -> layout
// Storage: data/kit_layouts.yml
// Methods: getLayout(UUID, modeId), setLayout(UUID, modeId, KitLayout), load(), save()
```

### New command: `/kitlayout` (alias: `/kl`)
```
/kitlayout — show current layout for current mode (if in fixed-kit duel)
/kitlayout edit — open layout editor (see below)
/kitlayout reset — reset to default layout
```
Permission: `duels.kitlayout` (default: true).

### Layout editor:
Open a Bukkit `Inventory` GUI (9 slots = hotbar representation). Show the fixed kit items. Player can move them around. On close (`InventoryCloseEvent`), save the new slot order as `KitLayout`.

Guard: only allow editing layout for modes where `mode instanceof FixedKitMode`. Show error otherwise.

### Integration:
In `FixedKitMode.onRoundStart()`, after building the kit ItemStack array, apply `KitLayoutManager.getLayout(playerId, modeId)` remap before giving items.

---

## Execution Order

Execute tasks in order: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10.

Before each task:
1. Read any files the task says to read first.
2. Use Grep to find all callers if renaming/adding methods.
3. Make minimal changes — no refactoring beyond scope.

After tasks 1–2, tasks 3–4, tasks 5, tasks 6–8, tasks 9–10: run a logical compile check (verify all imports, method signatures match, no missing implementations) and create a git commit.

If any task reveals a blocker (e.g. `Duel.java` already has ranked flag), adapt the implementation to match existing code — don't duplicate.

## Starting Point

Start with TASK 1. Read `config.yml` first to see current hub/lobby config structure, then proceed.
