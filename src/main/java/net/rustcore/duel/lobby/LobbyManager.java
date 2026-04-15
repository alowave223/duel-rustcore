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
  private final java.util.List<org.bukkit.Location> hubSpawns = new java.util.ArrayList<>();

  // slot -> LobbyItem
  private final Map<Integer, LobbyItem> lobbyItems = new LinkedHashMap<>();

  public final NamespacedKey LOBBY_ITEM_KEY;

  public LobbyManager(DuelsPlugin plugin) {
    this.plugin = plugin;
    this.LOBBY_ITEM_KEY = new NamespacedKey(plugin, "lobby_item_action");
  }

  public void load() {
    lobbyItems.clear();
    hubSpawns.clear();

    // Load lobby spawn
    loadHubSpawns();

    // Load lobby items
    ConfigurationSection itemsSection = plugin.getConfig().getConfigurationSection("lobby-items");
    if (itemsSection == null)
      return;

    for (String slotStr : itemsSection.getKeys(false)) {
      int slot;
      try {
        slot = Integer.parseInt(slotStr);
      } catch (NumberFormatException e) {
        continue;
      }

      ConfigurationSection itemSection = itemsSection.getConfigurationSection(slotStr);
      if (itemSection == null)
        continue;

      Material material = Material.valueOf(itemSection.getString("material", "STONE"));
      String name = itemSection.getString("name", "");
      List<String> lore = itemSection.getStringList("lore");
      int cmd = itemSection.getInt("custom-model-data", 0);
      List<ItemAction> actions = new ArrayList<>();
      List<Map<?, ?>> execList = itemSection.getMapList("execute-on-use");
      for (Map<?, ?> entry : execList) {
        Object typeObj = entry.get("type");
        Object cmdObj = entry.get("command");
        String type = typeObj != null ? String.valueOf(typeObj) : "player";
        String command = cmdObj != null ? String.valueOf(cmdObj) : "";
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
    if (item == null || !item.hasItemMeta())
      return false;
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

  private void loadHubSpawns() {
    hubSpawns.clear();
    java.util.List<java.util.Map<?, ?>> raw = plugin.getConfig().getMapList("hubs");
    if (raw.isEmpty()) {
      plugin.getLogger().warning("No 'hubs:' entries in config.yml — /hub has no targets.");
      return;
    }
    for (java.util.Map<?, ?> entry : raw) {
      Object worldObj = entry.get("world");
      String worldName = worldObj != null ? String.valueOf(worldObj) : "lobby";
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

  public Location getHubSpawn(int index) {
    if (hubSpawns.isEmpty()) { loadHubSpawns(); }
    if (index < 0 || index >= hubSpawns.size()) return null;
    Location loc = hubSpawns.get(index);
    if (loc.getWorld() == null) {
      java.util.List<java.util.Map<?, ?>> raw = plugin.getConfig().getMapList("hubs");
      if (index < raw.size()) {
        Object worldObj = raw.get(index).get("world");
        String worldName = worldObj != null ? String.valueOf(worldObj) : "lobby";
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

  public void reload() {
    load();
  }

  public record ItemAction(String type, String command) {
  }

  public record LobbyItem(ItemStack item, List<ItemAction> actions, int slot) {
  }
}
