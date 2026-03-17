package net.rustcore.duel.kit;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.util.CC;
import net.rustcore.duel.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Manages the kit drafting menu for KitBuilder mode.
 * Reads item definitions from the mode's yml config and presents
 * a chest GUI to players where they click to add items to inventory.
 */
public class KitMenu {

    // Menu layout
    private Component menuTitle = CC.parse("<dark_purple><bold>Draft Your Kit");
    private int menuRows = 6;
    private ItemStack fillerItem;
    private ItemStack readyButton;
    private int readySlot;
    private ItemStack clearButton;
    private int clearSlot;
    private ItemStack infoItem;
    private int infoSlot;
    private int draftTimeLimit;
    private boolean loaded = false;

    // Available items: slot -> MenuEntry
    private final Map<Integer, MenuEntry> menuItems = new LinkedHashMap<>();

    // Track picks per player: playerUUID -> (slot -> pick count)
    private final Map<UUID, Map<Integer, Integer>> playerPicks = new HashMap<>();

    // Track which inventories belong to kit menus
    private final Set<Inventory> activeMenus = Collections.newSetFromMap(new WeakHashMap<>());

    // PDC keys
    public final NamespacedKey KEY_MENU_TYPE;
    public final NamespacedKey KEY_SLOT_INDEX;

    public KitMenu(DuelsPlugin plugin) {
        this.KEY_MENU_TYPE = new NamespacedKey(plugin, "kit_menu_type");
        this.KEY_SLOT_INDEX = new NamespacedKey(plugin, "kit_slot_index");
    }

    /**
     * Load menu configuration from a mode's YAML config.
     */
    public void load(YamlConfiguration config) {
        menuItems.clear();

        ConfigurationSection menuSection = config.getConfigurationSection("kit-menu");
        if (menuSection == null) return;

        menuTitle = CC.parse(menuSection.getString("title", "<dark_purple><bold>Draft Your Kit"));
        menuRows = Math.max(1, Math.min(6, menuSection.getInt("rows", 6)));
        draftTimeLimit = menuSection.getInt("draft-time-limit", 30);
        loaded = true;

        // Filler
        ConfigurationSection fillerSection = menuSection.getConfigurationSection("filler");
        if (fillerSection != null) {
            Material fillerMat = Material.valueOf(fillerSection.getString("material", "GRAY_STAINED_GLASS_PANE"));
            fillerItem = new ItemBuilder(fillerMat)
                    .name(fillerSection.getString("name", " "))
                    .build();
        } else {
            fillerItem = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        }

        // Ready button
        ConfigurationSection readySection = menuSection.getConfigurationSection("ready-button");
        if (readySection != null) {
            readySlot = readySection.getInt("slot", 49);
            Material readyMat = Material.valueOf(readySection.getString("material", "LIME_DYE"));
            readyButton = new ItemBuilder(readyMat)
                    .name(readySection.getString("name", "<green><bold>READY"))
                    .lore(readySection.getStringList("lore"))
                    .pdc(KEY_MENU_TYPE, "ready")
                    .build();
        }

        // Clear button
        ConfigurationSection clearSection = menuSection.getConfigurationSection("clear-button");
        if (clearSection != null) {
            clearSlot = clearSection.getInt("slot", 48);
            Material clearMat = Material.valueOf(clearSection.getString("material", "RED_DYE"));
            clearButton = new ItemBuilder(clearMat)
                    .name(clearSection.getString("name", "<red><bold>CLEAR"))
                    .lore(clearSection.getStringList("lore"))
                    .pdc(KEY_MENU_TYPE, "clear")
                    .build();
        }

        // Info item
        ConfigurationSection infoSection = menuSection.getConfigurationSection("info-item");
        if (infoSection != null) {
            infoSlot = infoSection.getInt("slot", 4);
            Material infoMat = Material.valueOf(infoSection.getString("material", "BOOK"));
            infoItem = new ItemBuilder(infoMat)
                    .name(infoSection.getString("name", "<yellow><bold>How It Works"))
                    .lore(infoSection.getStringList("lore"))
                    .pdc(KEY_MENU_TYPE, "info")
                    .build();
        }

        // Parse items
        List<?> itemsList = menuSection.getList("items");
        if (itemsList != null) {
            for (Object itemObj : itemsList) {
                if (itemObj instanceof Map<?, ?> itemMap) {
                    int slot = ((Number) itemMap.get("slot")).intValue();
                    int max = itemMap.containsKey("max") ? ((Number) itemMap.get("max")).intValue() : 0;
                    int amount = itemMap.containsKey("amount") ? ((Number) itemMap.get("amount")).intValue() : 1;

                    ItemStack displayItem = KitItemParser.parse((Map<?, ?>) itemMap);
                    ItemStack giveItem = displayItem.clone();

                    // Add pick info to display lore
                    ItemBuilder displayBuilder = new ItemBuilder(displayItem);
                    if (max > 0) {
                        displayBuilder.addLore(" ");
                        displayBuilder.addLore("<gray>Max picks: <white>" + max);
                        displayBuilder.addLore("<gray>Amount per pick: <white>" + amount);
                    }
                    displayBuilder.pdc(KEY_MENU_TYPE, "item");
                    displayBuilder.pdc(KEY_SLOT_INDEX, slot);

                    menuItems.put(slot, new MenuEntry(displayBuilder.build(), giveItem, amount, max));
                }
            }
        }
    }

    /**
     * Open the kit drafting menu for a player.
     */
    public Inventory open(Player player) {
        Inventory inv = Bukkit.createInventory(null, menuRows * 9, menuTitle);
        activeMenus.add(inv);

        // Fill with filler
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, fillerItem);
        }

        // Place items
        for (Map.Entry<Integer, MenuEntry> entry : menuItems.entrySet()) {
            if (entry.getKey() < inv.getSize()) {
                inv.setItem(entry.getKey(), entry.getValue().displayItem());
            }
        }

        // Place buttons
        if (readyButton != null && readySlot < inv.getSize()) inv.setItem(readySlot, readyButton);
        if (clearButton != null && clearSlot < inv.getSize()) inv.setItem(clearSlot, clearButton);
        if (infoItem != null && infoSlot < inv.getSize()) inv.setItem(infoSlot, infoItem);

        // Reset picks
        playerPicks.put(player.getUniqueId(), new HashMap<>());

        player.openInventory(inv);
        return inv;
    }

    /**
     * Handle a click in the kit menu.
     * @return true if the click was handled
     */
    public boolean handleClick(Player player, int slot, Inventory clickedInventory) {
        if (!activeMenus.contains(clickedInventory)) return false;

        // Check ready button
        if (slot == readySlot) {
            return true; // Handled by KitMenuListener which calls onPlayerReady
        }

        // Check clear button
        if (slot == clearSlot) {
            clearPlayerPicks(player);
            return true;
        }

        // Check info slot
        if (slot == infoSlot) {
            return true; // Do nothing
        }

        // Check item slots
        MenuEntry entry = menuItems.get(slot);
        if (entry == null) return true; // Clicked filler

        UUID playerId = player.getUniqueId();
        Map<Integer, Integer> picks = playerPicks.computeIfAbsent(playerId, k -> new HashMap<>());
        int currentPicks = picks.getOrDefault(slot, 0);

        if (entry.maxPicks() > 0 && currentPicks >= entry.maxPicks()) {
            player.sendMessage(CC.parse("<red>You've already picked the maximum amount of this item!"));
            return true;
        }

        // Give item to player
        player.getInventory().addItem(entry.giveItem().clone());
        picks.put(slot, currentPicks + 1);

        // Update display to show remaining picks
        if (entry.maxPicks() > 0) {
            int remaining = entry.maxPicks() - currentPicks - 1;
            if (remaining <= 0) {
                // Mark as depleted
                ItemStack depleted = new ItemBuilder(Material.BARRIER)
                        .name("<red><strikethrough>Depleted")
                        .addLore("<gray>You've used all picks for this item")
                        .pdc(KEY_MENU_TYPE, "item")
                        .pdc(KEY_SLOT_INDEX, slot)
                        .build();
                clickedInventory.setItem(slot, depleted);
            }
        }

        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
        return true;
    }

    /**
     * Clear all picks for a player and reset their inventory (except armor/offhand).
     */
    public void clearPlayerPicks(Player player) {
        // Clear main inventory only (not armor or offhand)
        for (int i = 0; i < 36; i++) {
            player.getInventory().setItem(i, null);
        }
        playerPicks.put(player.getUniqueId(), new HashMap<>());

        // Refresh the menu if open
        if (player.getOpenInventory().getTopInventory() != null
                && activeMenus.contains(player.getOpenInventory().getTopInventory())) {
            Inventory inv = player.getOpenInventory().getTopInventory();
            for (Map.Entry<Integer, MenuEntry> entry : menuItems.entrySet()) {
                if (entry.getKey() < inv.getSize()) {
                    inv.setItem(entry.getKey(), entry.getValue().displayItem());
                }
            }
        }

        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
    }

    public void removePlayer(UUID playerId) {
        playerPicks.remove(playerId);
    }

    public void removeMenu(Inventory inv) {
        activeMenus.remove(inv);
    }

    public boolean isKitMenu(Inventory inv) {
        return activeMenus.contains(inv);
    }

    public int getDraftTimeLimit() { return draftTimeLimit; }

    public boolean isLoaded() { return loaded; }

    public record MenuEntry(ItemStack displayItem, ItemStack giveItem, int giveAmount, int maxPicks) {}
}
