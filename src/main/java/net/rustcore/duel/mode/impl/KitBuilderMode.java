package net.rustcore.duel.mode.impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.duel.Duel;
import net.rustcore.duel.kit.KitMenu;
import net.rustcore.duel.mode.DuelMode;
import net.rustcore.duel.modification.Modification;
import net.rustcore.duel.util.CC;
import net.rustcore.duel.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

/**
 * Kit Builder mode: players draft their own loadout from a configurable menu.
 * Default equipment (maxed netherite armor + totem offhand) is auto-equipped.
 */
public class KitBuilderMode implements DuelMode {

    private final DuelsPlugin plugin;
    private YamlConfiguration config;
    private final KitMenu kitMenu;

    // Config values
    private String id;
    private String displayName;
    private String description;
    private Material icon;
    private boolean enabled;
    private List<Integer> availableBestOf;
    private int defaultBestOf;
    private boolean redraftEachRound;
    private Modification modification;

    // Default equipment
    private ItemStack defaultHelmet;
    private ItemStack defaultChestplate;
    private ItemStack defaultLeggings;
    private ItemStack defaultBoots;
    private ItemStack defaultOffhand;

    public static final String META_READY    = "kb_ready";
    public static final String META_KITS     = "kb_kits";
    public static final String META_TIMER    = "kb_timer";
    public static final String META_TITLETIMER_PREFIX = "kb_title_timer_";

    public KitBuilderMode(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.kitMenu = new KitMenu(plugin);
    }

    public void load() {
        File configFile = new File(plugin.getDataFolder(), "modes/kitbuilder.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            try (InputStream in = plugin.getResource("modes/kitbuilder.yml")) {
                if (in != null) Files.copy(in, configFile.toPath());
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save default kitbuilder.yml: " + e.getMessage());
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        // Mode settings
        ConfigurationSection modeSection = config.getConfigurationSection("mode");
        if (modeSection != null) {
            id = modeSection.getString("id", "kitbuilder");
            displayName = modeSection.getString("display-name", "<gradient:#a855f7:#ec4899>Kit Builder</gradient>");
            description = modeSection.getString("description", "Build your own loadout and fight!");
            icon = Material.valueOf(modeSection.getString("icon", "NETHERITE_SWORD"));
            enabled = modeSection.getBoolean("enabled", true);
        }

        // Round settings
        ConfigurationSection roundSection = config.getConfigurationSection("rounds");
        if (roundSection != null) {
            availableBestOf = roundSection.getIntegerList("available");
            if (availableBestOf.isEmpty()) availableBestOf = List.of(1, 3, 5);
            defaultBestOf = roundSection.getInt("default", 1);
            redraftEachRound = roundSection.getBoolean("redraft-each-round", true);
        } else {
            availableBestOf = List.of(1, 3, 5);
            defaultBestOf = 1;
            redraftEachRound = true;
        }

        // Modifications
        modification = Modification.fromConfig(config.getConfigurationSection("modifications"));

        // Default equipment
        loadDefaultEquipment();

        // Kit menu
        kitMenu.load(config);

        plugin.getLogger().info("Loaded Kit Builder mode");
    }

    private void loadDefaultEquipment() {
        ConfigurationSection equipSection = config.getConfigurationSection("default-equipment");
        if (equipSection == null) {
            // Fallback defaults
            defaultHelmet = createMaxedArmor(Material.NETHERITE_HELMET);
            defaultChestplate = createMaxedArmor(Material.NETHERITE_CHESTPLATE);
            defaultLeggings = createMaxedArmor(Material.NETHERITE_LEGGINGS);
            defaultBoots = createMaxedBoots();
            defaultOffhand = new ItemStack(Material.TOTEM_OF_UNDYING);
            return;
        }

        defaultHelmet = parseEquipmentPiece(equipSection.getConfigurationSection("helmet"));
        defaultChestplate = parseEquipmentPiece(equipSection.getConfigurationSection("chestplate"));
        defaultLeggings = parseEquipmentPiece(equipSection.getConfigurationSection("leggings"));
        defaultBoots = parseEquipmentPiece(equipSection.getConfigurationSection("boots"));

        ConfigurationSection offhandSection = equipSection.getConfigurationSection("offhand");
        if (offhandSection != null) {
            Material mat = Material.valueOf(offhandSection.getString("material", "TOTEM_OF_UNDYING"));
            int amount = offhandSection.getInt("amount", 1);
            defaultOffhand = new ItemStack(mat, amount);
        } else {
            defaultOffhand = new ItemStack(Material.TOTEM_OF_UNDYING);
        }
    }

    private ItemStack parseEquipmentPiece(ConfigurationSection section) {
        if (section == null) return null;
        Material mat = Material.valueOf(section.getString("material", "AIR"));
        ItemBuilder builder = new ItemBuilder(mat);

        ConfigurationSection enchants = section.getConfigurationSection("enchantments");
        if (enchants != null) {
            Map<String, Integer> enchantMap = new LinkedHashMap<>();
            for (String key : enchants.getKeys(false)) {
                enchantMap.put(key, enchants.getInt(key));
            }
            builder.enchantments(enchantMap);
        }

        return builder.build();
    }

    private ItemStack createMaxedArmor(Material material) {
        return new ItemBuilder(material)
                .enchant(Enchantment.PROTECTION, 4)
                .enchant(Enchantment.UNBREAKING, 3)
                .enchant(Enchantment.MENDING, 1)
                .build();
    }

    private ItemStack createMaxedBoots() {
        return new ItemBuilder(Material.NETHERITE_BOOTS)
                .enchant(Enchantment.PROTECTION, 4)
                .enchant(Enchantment.UNBREAKING, 3)
                .enchant(Enchantment.MENDING, 1)
                .enchant(Enchantment.DEPTH_STRIDER, 3)
                .build();
    }

    @Override
    public void onRoundSetup(Duel duel, Player player) {
        int currentRound = duel.getCurrentRound();

        // Clear inventory
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);

        // Equip default armor
        player.getInventory().setHelmet(defaultHelmet != null ? defaultHelmet.clone() : null);
        player.getInventory().setChestplate(defaultChestplate != null ? defaultChestplate.clone() : null);
        player.getInventory().setLeggings(defaultLeggings != null ? defaultLeggings.clone() : null);
        player.getInventory().setBoots(defaultBoots != null ? defaultBoots.clone() : null);
        player.getInventory().setItemInOffHand(defaultOffhand != null ? defaultOffhand.clone() : null);

        // Full health, food, and clear effects
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExhaustion(0f);
        player.setFireTicks(0);
        player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));

        // Ensure ready set exists on the duel
        if (duel.getMeta(META_READY) == null) duel.setMeta(META_READY, new HashSet<UUID>());

        // Check if we should redraft or use saved kit
        if (!redraftEachRound && currentRound > 1 && hasSavedKit(duel, player.getUniqueId())) {
            restoreSavedKit(duel, player);
            this.<Set<UUID>>duelMeta(duel, META_READY).add(player.getUniqueId());
            player.sendMessage(CC.parse(plugin.getMessage("kit-ready")));
        } else {
            UUID playerId = player.getUniqueId();

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player p = Bukkit.getPlayer(playerId);
                if (p == null || !p.isOnline()) return;
                try {
                    kitMenu.open(p);
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to open kit menu for " + p.getName() + ": " + e.getMessage());
                    p.sendMessage(CC.parse(plugin.getMessage("kit-menu-failed")));
                }
            }, 5L);

            Component actionBarMsg = MiniMessage.miniMessage().deserialize("<green>Чтобы открыть меню выбора используйте команду /draftmenu");

            if (duel.getMeta(META_TITLETIMER_PREFIX + player.getUniqueId()) == null) {
                BukkitTask subTitleTimer = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendActionBar(actionBarMsg);
                    } else {
                        cancelTitleTimer(duel, player);
                    }
                }, 0L, 35L);

                duel.setMeta(META_TITLETIMER_PREFIX + player.getUniqueId(), subTitleTimer);
            }
        }

        // Schedule draft timeout once per duel round (only on first player setup call)
        int timeLimit = kitMenu.getDraftTimeLimit();
        if (timeLimit > 0 && duel.getMeta(META_TIMER) == null) {
            BukkitTask timer = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                duel.removeMeta(META_TIMER);
                autoReadyPending(duel);
            }, timeLimit * 20L);
            duel.setMeta(META_TIMER, timer);
        }
    }

    /**
     * Auto-ready any players who haven't readied themselves when the draft timer expires.
     */
    private void autoReadyPending(Duel duel) {
        if (duel.getState() != net.rustcore.duel.duel.DuelState.DRAFTING) return;
        Set<UUID> ready = duelMeta(duel, META_READY);
        if (ready == null) return;
        for (Player player : duel.getPlayers()) {
            if (!ready.contains(player.getUniqueId())) {
                player.sendMessage(CC.parse(plugin.getMessage("draft-timeout")));
                onPlayerReady(duel, player);
            }
        }
    }

    private <T> T duelMeta(Duel duel, String key) {
        return duel.getMeta(key);
    }

    @Override
    public void onPlayerReady(Duel duel, Player player) {
        if (duel.getMeta(META_READY) == null) duel.setMeta(META_READY, new HashSet<UUID>());
        Set<UUID> ready = duelMeta(duel, META_READY);
        ready.add(player.getUniqueId());

        // Save kit for non-redraft rounds
        if (!redraftEachRound) {
            saveKit(duel, player);
        }

        // Close the menu
        player.closeInventory();
        player.sendMessage(CC.parse(plugin.getMessage("kit-ready")));

        // Check if all players are ready (use playerIds, not getPlayers(), so an
        // offline/lagging player does not shrink the set and trigger a false allReady)
        boolean allReady = duel.getPlayerIds().stream()
                .allMatch(ready::contains);

        cancelTitleTimer(duel, player);
        
        if (allReady) {
            cancelDraftTimer(duel);
            duel.startCountdown();
        }
    }

    private void cancelDraftTimer(Duel duel) {
        BukkitTask timer = duelMeta(duel, META_TIMER);
        if (timer != null) timer.cancel();
        duel.removeMeta(META_TIMER);
    }

    public void cancelTitleTimer(Duel duel, Player player) {
        BukkitTask timer = duelMeta(duel, META_TITLETIMER_PREFIX + player.getUniqueId());
        if (timer != null) {
            timer.cancel();
            player.clearTitle();
        }

        duel.removeMeta(META_TITLETIMER_PREFIX + player.getUniqueId());
    }

    @Override
    public void onRoundStart(Duel duel) {
        duel.removeMeta(META_READY);
    }

    @Override
    public boolean onPlayerDeath(Duel duel, Player dead, Player killer) {
        // In kit builder, any death counts as a round loss
        return true;
    }

    @Override
    public void onDuelEnd(Duel duel) {
        cancelDraftTimer(duel);
        duel.removeMeta(META_READY);
        duel.removeMeta(META_KITS);
        for (Player player : duel.getPlayers()) {
            cancelTitleTimer(duel, player);
            kitMenu.removePlayer(player.getUniqueId());
        }
    }

    private void saveKit(Duel duel, Player player) {
        Map<UUID, ItemStack[]> kits = duel.getMeta(META_KITS);
        if (kits == null) {
            kits = new HashMap<>();
            duel.setMeta(META_KITS, kits);
        }
        kits.put(player.getUniqueId(), player.getInventory().getContents().clone());
    }

    private boolean hasSavedKit(Duel duel, UUID playerId) {
        Map<UUID, ItemStack[]> kits = duel.getMeta(META_KITS);
        return kits != null && kits.containsKey(playerId);
    }

    private void restoreSavedKit(Duel duel, Player player) {
        Map<UUID, ItemStack[]> kits = duel.getMeta(META_KITS);
        if (kits == null) return;
        ItemStack[] saved = kits.get(player.getUniqueId());
        if (saved != null) {
            for (int i = 0; i < Math.min(saved.length, 36); i++) {
                player.getInventory().setItem(i, saved[i] != null ? saved[i].clone() : null);
            }
        }
    }

    public KitMenu getKitMenu() { return kitMenu; }

    // DuelMode interface
    @Override public String getId() { return id; }
    @Override public String getDisplayName() { return displayName; }
    @Override public String getDescription() { return description; }
    @Override public Material getIcon() { return icon; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public List<Integer> getAvailableBestOf() { return availableBestOf; }
    @Override public int getDefaultBestOf() { return defaultBestOf; }
    @Override public boolean isRedraftEachRound() { return redraftEachRound; }
    @Override public Modification getModification() { return modification; }

    @Override
    public void reload() {
        load();
    }
}
