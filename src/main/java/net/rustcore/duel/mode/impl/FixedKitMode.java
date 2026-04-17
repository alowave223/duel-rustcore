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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public ItemStack getHotbarItem(int slot) { return hotbar.get(slot); }

    @Override
    public boolean onPlayerDeath(Duel duel, Player dead, Player killer) {
        return true;
    }

    @Override
    public void onDuelEnd(Duel duel) { /* nothing */ }

    @Override
    public void reload() { /* reloaded via ModeManager */ }
}
