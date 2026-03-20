package net.rustcore.duel.lobby;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Manages the lobby spawn, hotbar items, and player transitions.
 */
public class LobbyManager {

    private final DuelsPlugin plugin;
    private Location lobbySpawn;

    // slot -> LobbyItem
    private final Map<Integer, LobbyItem> lobbyItems = new LinkedHashMap<>();

    public final NamespacedKey LOBBY_ITEM_KEY;

    public LobbyManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.LOBBY_ITEM_KEY = new NamespacedKey(plugin, "lobby_item_action");
    }

    public void load() {
        lobbyItems.clear();
        lobbySpawn = null;

        // Load lobby spawn
        loadLobbySpawn();

        // Load lobby items
        ConfigurationSection itemsSection = plugin.getConfig().getConfigurationSection("lobby-items");
        if (itemsSection == null) return;

        for (String slotStr : itemsSection.getKeys(false)) {
            int slot;
            try {
                slot = Integer.parseInt(slotStr);
            } catch (NumberFormatException e) {
                continue;
            }

            ConfigurationSection itemSection = itemsSection.getConfigurationSection(slotStr);
            if (itemSection == null) continue;

            Material material = Material.valueOf(itemSection.getString("material", "STONE"));
            String name = itemSection.getString("name", "");
            List<String> lore = itemSection.getStringList("lore");
            int cmd = itemSection.getInt("custom-model-data", 0);
            List<ItemAction> actions = new ArrayList<>();
            List<Map<?, ?>> execList = itemSection.getMapList("execute-on-use");
            for (Map<?, ?> entry : execList) {
                String type = String.valueOf(entry.getOrDefault("type", "player"));
                String command = String.valueOf(entry.getOrDefault("command", ""));
                if (!command.isEmpty()) {
                    actions.add(new ItemAction(type, command));
                }
            }

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

            ItemBuilder builder = new ItemBuilder(material)
                    .name(name)
                    .lore(lore)
                    .customModelData(cmd)
                    .pdc(LOBBY_ITEM_KEY, "true");
            if (!flags.isEmpty()) {
                builder.flags(flags.toArray(new ItemFlag[0]));
            }
            ItemStack item = builder.build();

            lobbyItems.put(slot, new LobbyItem(item, actions, slot));
        }

        plugin.getLogger().info("Loaded " + lobbyItems.size() + " lobby items");
    }

    /**
     * Send a player to the lobby. Clears inventory and gives lobby items.
     */
    public void sendToLobby(Player player) {
        // Clear everything
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

        // Teleport
        Location spawn = getLobbySpawn();
        if (spawn != null) {
            player.teleport(spawn);
        }

        // Give lobby items
        giveLobbyItems(player);
    }

    /**
     * Give lobby items to a player without teleporting.
     */
    public void giveLobbyItems(Player player) {
        for (Map.Entry<Integer, LobbyItem> entry : lobbyItems.entrySet()) {
            player.getInventory().setItem(entry.getKey(), entry.getValue().item().clone());
        }
    }

    /**
     * Get the actions for a lobby item by checking the player's held slot.
     */
    public List<ItemAction> getLobbyItemActions(int slot) {
        LobbyItem lobbyItem = lobbyItems.get(slot);
        return lobbyItem != null ? lobbyItem.actions() : List.of();
    }

    /**
     * Check if an item is a lobby item.
     */
    public boolean isLobbyItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String val = item.getItemMeta().getPersistentDataContainer()
                .get(LOBBY_ITEM_KEY, PersistentDataType.STRING);
        return val != null && !val.isEmpty();
    }

    /**
     * Check if a slot is a lobby item slot.
     */
    public boolean isLobbySlot(int slot) {
        return lobbyItems.containsKey(slot);
    }

    private void loadLobbySpawn() {
        String worldName = plugin.getConfig().getString("general.lobby-world", "lobby");
        double x = plugin.getConfig().getDouble("general.lobby-spawn.x", 0.5);
        double y = plugin.getConfig().getDouble("general.lobby-spawn.y", 64);
        double z = plugin.getConfig().getDouble("general.lobby-spawn.z", 0.5);
        float yaw = (float) plugin.getConfig().getDouble("general.lobby-spawn.yaw", 0);
        float pitch = (float) plugin.getConfig().getDouble("general.lobby-spawn.pitch", 0);

        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            lobbySpawn = new Location(world, x, y, z, yaw, pitch);
            plugin.getLogger().info("Lobby spawn set to " + x + ", " + y + ", " + z
                    + " in world '" + worldName + "'");
        } else {
            plugin.getLogger().warning("Lobby world '" + worldName + "' not found! "
                    + "Will attempt to find it when needed.");
        }
    }

    public Location getLobbySpawn() {
        if (lobbySpawn == null || lobbySpawn.getWorld() == null) {
            loadLobbySpawn();
        }
        return lobbySpawn;
    }

    public void reload() {
        load();
    }

    public record ItemAction(String type, String command) {}

    public record LobbyItem(ItemStack item, List<ItemAction> actions, int slot) {}
}
