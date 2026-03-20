# Lobby Item Flags Configuration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow server operators to configure `ItemFlag`s (e.g. `HIDE_ATTRIBUTES`, `HIDE_UNBREAKABLE`, `HIDE_ENCHANTS`) per lobby item via `config.yml`.

**Architecture:** Add an optional `item-flags` list to each lobby item entry in the config. During `LobbyManager.load()`, parse the list into `org.bukkit.inventory.ItemFlag` enums and pass them to the existing `ItemBuilder.flags()` method. No new files needed — only two existing files are modified plus the default config.

**Tech Stack:** Java 21, Paper API 1.21.4, Bukkit `ItemFlag` enum, existing `ItemBuilder` utility.

---

## File Structure

| Action | File | Responsibility |
|--------|------|---------------|
| Modify | `src/main/resources/config.yml` | Add `item-flags` list to each lobby item entry |
| Modify | `src/main/java/net/rustcore/duel/lobby/LobbyManager.java` | Parse `item-flags` from config and apply to ItemBuilder |

No new files are created. The existing `ItemBuilder.flags(ItemFlag...)` method already supports arbitrary flags — we just need to call it.

---

### Task 1: Add `item-flags` to default config

**Files:**
- Modify: `src/main/resources/config.yml:81-125` (lobby-items section)

- [ ] **Step 1: Add `item-flags` list to each lobby item in `config.yml`**

Add an `item-flags` key with the three default flags to every lobby item entry. Example for slot `0`:

```yaml
  0:
    material: DIAMOND_SWORD
    name: "<green><bold>Queue for Duel"
    lore:
      - "<gray>Click to join the duel queue"
    custom-model-data: 0
    action: "open_queue_menu"
    item-flags:
      - HIDE_ATTRIBUTES
      - HIDE_UNBREAKABLE
      - HIDE_ENCHANTS
```

Apply the same `item-flags` block to **all 6 lobby item entries** (slots 0, 1, 2, 4, 6, 8).

- [ ] **Step 2: Verify YAML is valid**

Run: `python -c "import yaml; yaml.safe_load(open('src/main/resources/config.yml'))"` or visually confirm indentation is correct. Each `item-flags` key must be at the same indentation level as `material`, `name`, etc.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/config.yml
git commit -m "config: add item-flags option to lobby items"
```

---

### Task 2: Parse `item-flags` in LobbyManager and apply to ItemBuilder

**Files:**
- Modify: `src/main/java/net/rustcore/duel/lobby/LobbyManager.java:45-73` (the load loop)

- [ ] **Step 1: Add the `ItemFlag` import**

Add to the import block at the top of `LobbyManager.java`:

```java
import org.bukkit.inventory.ItemFlag;
```

- [ ] **Step 2: Add flag parsing logic inside the load loop**

After the existing line that reads `action` (line 63) and before the `ItemBuilder` chain (line 65), add:

```java
            // Parse item flags
            List<String> flagNames = itemSection.getStringList("item-flags");
            List<ItemFlag> flags = new ArrayList<>();
            for (String flagName : flagNames) {
                try {
                    flags.add(ItemFlag.valueOf(flagName.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Unknown item flag '" + flagName
                            + "' in lobby-items." + slotStr + " — skipping.");
                }
            }
```

- [ ] **Step 3: Wire flags into the ItemBuilder chain**

Change the existing `ItemBuilder` chain from:

```java
            ItemStack item = new ItemBuilder(material)
                    .name(name)
                    .lore(lore)
                    .customModelData(cmd)
                    .pdc(LOBBY_ITEM_KEY, action)
                    .build();
```

To:

```java
            ItemBuilder builder = new ItemBuilder(material)
                    .name(name)
                    .lore(lore)
                    .customModelData(cmd)
                    .pdc(LOBBY_ITEM_KEY, action);
            if (!flags.isEmpty()) {
                builder.flags(flags.toArray(new ItemFlag[0]));
            }
            ItemStack item = builder.build();
```

- [ ] **Step 4: Verify the project compiles**

Run: `mvn compile -q` from the project root.
Expected: BUILD SUCCESS with no errors.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/rustcore/duel/lobby/LobbyManager.java
git commit -m "feat: parse item-flags from config for lobby items"
```

---

### Task 3: Manual smoke test

- [ ] **Step 1: Build the plugin JAR**

Run: `mvn package -q`
Expected: JAR created in `target/`.

- [ ] **Step 2: Test on a Paper server**

1. Drop the JAR into `plugins/`.
2. Start the server and join.
3. Verify lobby items appear without attribute/enchant/unbreakable tooltips.
4. Remove one flag (e.g. `HIDE_ENCHANTS`) from config, run `/duels reload`, confirm enchant tooltips reappear.
5. Add an invalid flag name (e.g. `FAKE_FLAG`), reload, confirm a warning is logged and the item still works.

---

## Valid `ItemFlag` values (for reference)

These are the Bukkit `ItemFlag` enum constants as of 1.21.4:

| Flag | Effect |
|------|--------|
| `HIDE_ENCHANTS` | Hides enchantment lines from tooltip |
| `HIDE_ATTRIBUTES` | Hides attribute modifiers (e.g. +7 Attack Damage) |
| `HIDE_UNBREAKABLE` | Hides the "Unbreakable" line |
| `HIDE_DESTROYS` | Hides "Can Break" lines |
| `HIDE_PLACED_ON` | Hides "Can be placed on" lines |
| `HIDE_ADDITIONAL_TOOLTIP` | Hides potion effects, shield pattern, firework info |
| `HIDE_DYE` | Hides dye color on leather armor |
| `HIDE_ARMOR_TRIM` | Hides armor trim description |

Operators can use any combination of these in their `item-flags` list.
