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
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Manages the kit drafting menu for KitBuilder mode.
 * Reads item definitions from the mode's yml config and presents
 * a chest GUI to players where they click to add items to inventory.
 * Supports pagination when items overflow a single page.
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
    private final DuelsPlugin plugin;

    // Available items: ordered list of (globalIndex, MenuEntry)
    // globalIndex is the original config slot used as a unique identifier
    private final List<IndexedEntry> allItems = new ArrayList<>();

    // Slot layout: the visual slot positions used for items (sorted from config)
    private List<Integer> itemSlotLayout = new ArrayList<>();

    // Track picks per player: playerUUID -> (globalIndex -> pick count)
    private final Map<UUID, Map<Integer, Integer>> playerPicks = new HashMap<>();

    // Track which inventories belong to kit menus
    private final Set<Inventory> activeMenus = Collections.newSetFromMap(new WeakHashMap<>());

    private final Map<UUID, Inventory> playerInventories = new HashMap<>();

    // Pagination: player -> current page
    private final Map<UUID, Integer> playerPages = new HashMap<>();

    // Pagination buttons
    private ItemStack prevPageButton;
    private ItemStack nextPageButton;
    private int prevPageSlot = 45;
    private int nextPageSlot = 53;

    // PDC keys
    public final NamespacedKey KEY_MENU_TYPE;
    public final NamespacedKey KEY_SLOT_INDEX;

    public KitMenu(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.KEY_MENU_TYPE = new NamespacedKey(plugin, "kit_menu_type");
        this.KEY_SLOT_INDEX = new NamespacedKey(plugin, "kit_slot_index");
    }

    /**
     * Load menu configuration from a mode's YAML config.
     */
    public void load(YamlConfiguration config) {
        allItems.clear();
        itemSlotLayout.clear();

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

        // Pagination config
        prevPageSlot = menuSection.getInt("prev-page-slot", 45);
        nextPageSlot = menuSection.getInt("next-page-slot", 53);

        prevPageButton = new ItemBuilder(Material.ARROW)
                .name(plugin.getMessage("kit-prev-page"))
                .pdc(KEY_MENU_TYPE, "page_prev")
                .build();

        nextPageButton = new ItemBuilder(Material.ARROW)
                .name(plugin.getMessage("kit-next-page"))
                .pdc(KEY_MENU_TYPE, "page_next")
                .build();

        // Parse items
        List<?> itemsList = menuSection.getList("items");
        if (itemsList != null) {
            Set<Integer> usedSlots = new TreeSet<>();
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
                        displayBuilder.addLore(plugin.getMessage("max-picks") + max);
                        displayBuilder.addLore(plugin.getMessage("amount-per-pick") + amount);
                    }
                    displayBuilder.pdc(KEY_MENU_TYPE, "item");
                    // globalIndex stored in PDC for click identification
                    displayBuilder.pdc(KEY_SLOT_INDEX, allItems.size());

                    allItems.add(new IndexedEntry(allItems.size(), new MenuEntry(displayBuilder.build(), giveItem, amount, max)));
                    usedSlots.add(slot);
                }
            }
            itemSlotLayout = new ArrayList<>(usedSlots);
        }
    }

    /**
     * Open the kit drafting menu for a player (page 0).
     */
    public Inventory open(Player player) {
        return openPage(player, 0, true);
    }

    /**
     * Open a specific page of the kit menu.
     * @param resetPicks if true, reset player's picks (first open of a round)
     */
    public Inventory openPage(Player player, int page, boolean resetPicks) {
        // Remove previous menu
        Inventory old = playerInventories.remove(player.getUniqueId());
        if (old != null) activeMenus.remove(old);

        int totalPages = getTotalPages();
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        playerPages.put(player.getUniqueId(), page);

        Inventory inv = Bukkit.createInventory(null, menuRows * 9, menuTitle);
        activeMenus.add(inv);
        playerInventories.put(player.getUniqueId(), inv);

        // Fill with filler
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, fillerItem);
        }

        // Place items for this page
        List<IndexedEntry> pageItems = getItemsForPage(page);
        Map<Integer, Integer> picks = playerPicks.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());

        for (int i = 0; i < pageItems.size() && i < itemSlotLayout.size(); i++) {
            int slot = itemSlotLayout.get(i);
            IndexedEntry ie = pageItems.get(i);
            int globalIndex = ie.index();
            MenuEntry entry = ie.entry();

            int currentPicks = picks.getOrDefault(globalIndex, 0);
            if (entry.maxPicks() > 0 && currentPicks >= entry.maxPicks()) {
                // Show depleted
                ItemStack depleted = new ItemBuilder(Material.BARRIER)
                        .name(plugin.getMessage("item-depleted-name"))
                        .addLore(plugin.getMessage("item-depleted-lore"))
                        .pdc(KEY_MENU_TYPE, "item")
                        .pdc(KEY_SLOT_INDEX, globalIndex)
                        .build();
                inv.setItem(slot, depleted);
            } else {
                // Re-tag display item with the global index
                ItemStack display = new ItemBuilder(entry.displayItem().clone())
                        .pdc(KEY_SLOT_INDEX, globalIndex)
                        .build();
                inv.setItem(slot, display);
            }
        }

        // Place buttons
        if (readyButton != null && readySlot < inv.getSize()) inv.setItem(readySlot, readyButton);
        if (clearButton != null && clearSlot < inv.getSize()) inv.setItem(clearSlot, clearButton);
        if (infoItem != null && infoSlot < inv.getSize()) inv.setItem(infoSlot, infoItem);

        // Place pagination buttons if needed
        if (totalPages > 1) {
            if (page > 0 && prevPageSlot < inv.getSize()) inv.setItem(prevPageSlot, prevPageButton);
            if (page < totalPages - 1 && nextPageSlot < inv.getSize()) inv.setItem(nextPageSlot, nextPageButton);
        }

        // Reset picks only on first open
        if (resetPicks) {
            playerPicks.put(player.getUniqueId(), new HashMap<>());
        }

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

        // Check pagination buttons
        if (slot == prevPageSlot || slot == nextPageSlot) {
            ItemStack clickedItem = clickedInventory.getItem(slot);
            if (clickedItem != null && clickedItem.hasItemMeta()) {
                String type = clickedItem.getItemMeta().getPersistentDataContainer()
                        .get(KEY_MENU_TYPE, PersistentDataType.STRING);
                if ("page_prev".equals(type)) {
                    int currentPage = playerPages.getOrDefault(player.getUniqueId(), 0);
                    if (currentPage > 0) openPage(player, currentPage - 1, false);
                    return true;
                } else if ("page_next".equals(type)) {
                    int currentPage = playerPages.getOrDefault(player.getUniqueId(), 0);
                    if (currentPage < getTotalPages() - 1) openPage(player, currentPage + 1, false);
                    return true;
                }
            }
            return true;
        }

        // Check item slots - read global index from PDC
        ItemStack clickedItem = clickedInventory.getItem(slot);
        if (clickedItem == null || !clickedItem.hasItemMeta()) return true;

        String menuType = clickedItem.getItemMeta().getPersistentDataContainer()
                .get(KEY_MENU_TYPE, PersistentDataType.STRING);
        if (!"item".equals(menuType)) return true;

        Integer globalIndex = clickedItem.getItemMeta().getPersistentDataContainer()
                .get(KEY_SLOT_INDEX, PersistentDataType.INTEGER);
        if (globalIndex == null || globalIndex < 0 || globalIndex >= allItems.size()) return true;

        MenuEntry entry = allItems.get(globalIndex).entry();

        UUID playerId = player.getUniqueId();
        Map<Integer, Integer> picks = playerPicks.computeIfAbsent(playerId, k -> new HashMap<>());
        int currentPicks = picks.getOrDefault(globalIndex, 0);

        if (entry.maxPicks() > 0 && currentPicks >= entry.maxPicks()) {
            player.sendMessage(CC.parse(plugin.getMessage("item-max-picks-reached")));
            return true;
        }

        // Give item to player
        ItemStack giveItem = entry.giveItem().clone();
        giveItem.setAmount(entry.giveAmount());
        player.getInventory().addItem(giveItem);
        picks.put(globalIndex, currentPicks + 1);

        // Update display to show remaining picks
        if (entry.maxPicks() > 0) {
            int remaining = entry.maxPicks() - currentPicks - 1;
            if (remaining <= 0) {
                ItemStack depleted = new ItemBuilder(Material.BARRIER)
                        .name(plugin.getMessage("item-depleted-name"))
                        .addLore(plugin.getMessage("item-depleted-lore"))
                        .pdc(KEY_MENU_TYPE, "item")
                        .pdc(KEY_SLOT_INDEX, globalIndex)
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

        // Refresh current page
        int page = playerPages.getOrDefault(player.getUniqueId(), 0);
        openPage(player, page, false);

        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
    }

    public void removePlayer(UUID playerId) {
        playerPicks.remove(playerId);
        playerPages.remove(playerId);
    }

    public void removeMenu(Inventory inv) {
        activeMenus.remove(inv);
        playerInventories.values().removeIf(v -> v.equals(inv));
    }

    public boolean isKitMenu(Inventory inv) {
        return activeMenus.contains(inv);
    }

    public Inventory getPlayerMenu(UUID playerId) {
        return playerInventories.get(playerId);
    }

    public int getPlayerPage(UUID playerId) {
        return playerPages.getOrDefault(playerId, 0);
    }

    public int getDraftTimeLimit() { return draftTimeLimit; }

    public boolean isLoaded() { return loaded; }

    private int getItemsPerPage() {
        return itemSlotLayout.size();
    }

    private int getTotalPages() {
        int perPage = getItemsPerPage();
        if (perPage <= 0) return 1;
        return Math.max(1, (int) Math.ceil((double) allItems.size() / perPage));
    }

    private List<IndexedEntry> getItemsForPage(int page) {
        int perPage = getItemsPerPage();
        if (perPage <= 0) return List.of();
        int start = page * perPage;
        int end = Math.min(start + perPage, allItems.size());
        if (start >= allItems.size()) return List.of();
        return allItems.subList(start, end);
    }

    public record MenuEntry(ItemStack displayItem, ItemStack giveItem, int giveAmount, int maxPicks) {}

    private record IndexedEntry(int index, MenuEntry entry) {}
}
