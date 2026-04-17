package net.rustcore.duel.kit;

import net.rustcore.duel.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parses item identifiers from config files.
 * Supports:
 *   minecraft:<material_name>  - Vanilla items
 *   executableitems:<item_id>  - ExecutableItems custom items
 */
public final class KitItemParser {

    private static final Logger LOGGER = Logger.getLogger("Duels");
    private static Method eiGetItemMethod;
    private static Object eiManagerInstance;
    private static boolean eiChecked = false;

    private KitItemParser() {}

    /**
     * Parse an item from a config section map.
     */
    public static ItemStack parse(Map<?, ?> itemData) {
        String id = (String) itemData.get("id");
        int amount = itemData.containsKey("amount") ? ((Number) itemData.get("amount")).intValue() : 1;

        if (id == null || id.isEmpty()) {
            LOGGER.warning("Item config missing 'id' field");
            return new ItemStack(Material.BARRIER);
        }

        ItemStack base = parseItemId(id, amount);
        if (base == null) {
            LOGGER.warning("Failed to parse item ID: " + id);
            return new ItemBuilder(Material.BARRIER, 1)
                    .name("<red>Invalid Item: " + id)
                    .build();
        }

        // For ExecutableItems, return as-is (they handle their own meta)
        if (id.toLowerCase().startsWith("executableitems:")) {
            base.setAmount(amount);
            return base;
        }

        // Apply additional meta for vanilla items
        ItemBuilder builder = new ItemBuilder(base);
        builder.amount(amount);

        if (itemData.containsKey("name")) {
            builder.name((String) itemData.get("name"));
        }

        if (itemData.containsKey("lore")) {
            @SuppressWarnings("unchecked")
            List<String> lore = (List<String>) itemData.get("lore");
            builder.lore(lore);
        }

        if (itemData.containsKey("enchantments")) {
            @SuppressWarnings("unchecked")
            Map<String, Integer> enchants = (Map<String, Integer>) itemData.get("enchantments");
            builder.enchantments(enchants);
        }

        if (itemData.containsKey("potion-type")) {
            builder.potionType((String) itemData.get("potion-type"));
        }

        return builder.build();
    }

    /**
     * Parse a raw item ID string into an ItemStack.
     */
    public static ItemStack parseItemId(String id, int amount) {
        if (id == null) return null;

        String[] parts = id.split(":", 2);
        if (parts.length != 2) {
            // Try as plain material name
            try {
                Material mat = Material.valueOf(id.toUpperCase());
                return new ItemStack(mat, amount);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        String namespace = parts[0].toLowerCase();
        String itemId = parts[1];

        return switch (namespace) {
            case "minecraft" -> parseVanillaItem(itemId, amount);
            case "executableitems" -> parseExecutableItem(itemId, amount);
            default -> {
                // If the first token is a vanilla material (e.g. "SPLASH_POTION:INSTANT_HEAL:2"),
                // treat the whole string as a potion descriptor rather than a namespaced id.
                try {
                    Material.valueOf(parts[0].toUpperCase());
                    yield parsePotionItem(id, amount);
                } catch (IllegalArgumentException ignored) {
                    LOGGER.warning("Unknown item namespace: " + namespace);
                    yield null;
                }
            }
        };
    }

    private static ItemStack parseVanillaItem(String materialName, int amount) {
        // Support "MATERIAL:EFFECT:LEVEL" (e.g. "SPLASH_POTION:INSTANT_HEAL:2")
        if (materialName.contains(":")) {
            return parsePotionItem(materialName, amount);
        }
        try {
            Material mat = Material.valueOf(materialName.toUpperCase());
            return new ItemStack(mat, amount);
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Unknown material: " + materialName);
            return null;
        }
    }

    /**
     * Parse a potion descriptor: MATERIAL:EFFECT:LEVEL[:DURATION_TICKS]
     * E.g. SPLASH_POTION:INSTANT_HEAL:2 or POTION:SPEED:2:3600
     */
    private static ItemStack parsePotionItem(String descriptor, int amount) {
        String[] parts = descriptor.split(":");
        if (parts.length < 3) {
            LOGGER.warning("Invalid potion descriptor (need MATERIAL:EFFECT:LEVEL): " + descriptor);
            return null;
        }
        Material mat;
        try {
            mat = Material.valueOf(parts[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Unknown material in potion descriptor: " + parts[0]);
            return null;
        }
        PotionEffectType effectType = PotionEffectType.getByName(parts[1].toUpperCase());
        if (effectType == null) {
            LOGGER.warning("Unknown potion effect: " + parts[1]);
            return null;
        }
        int level;
        try {
            level = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid potion level: " + parts[2]);
            return null;
        }
        int durationTicks;
        if (parts.length >= 4) {
            try {
                durationTicks = Integer.parseInt(parts[3]);
            } catch (NumberFormatException e) {
                LOGGER.warning("Invalid potion duration: " + parts[3]);
                return null;
            }
        } else {
            // Instant effects use duration 1; continuous effects default to very long duration.
            durationTicks = effectType.isInstant() ? 1 : 1_000_000;
        }

        ItemStack stack = new ItemStack(mat, amount);
        if (stack.getItemMeta() instanceof PotionMeta pm) {
            pm.addCustomEffect(new PotionEffect(effectType, durationTicks, Math.max(0, level - 1)), true);
            stack.setItemMeta(pm);
        }
        return stack;
    }

    private static ItemStack parseExecutableItem(String itemId, int amount) {
        if (!eiChecked) {
            eiChecked = true;
            try {
                Plugin eiPlugin = Bukkit.getPluginManager().getPlugin("ExecutableItems");
                if (eiPlugin == null) {
                    LOGGER.warning("ExecutableItems not found! Cannot parse executableitems: items.");
                    return null;
                }
                // Use reflection to avoid hard dependency
                Class<?> managerClass = Class.forName("com.ssomar.score.api.executableitems.ExecutableItemsAPI");
                Method getManagerMethod = managerClass.getMethod("getExecutableItemsManager");
                eiManagerInstance = getManagerMethod.invoke(null);
                eiGetItemMethod = eiManagerInstance.getClass().getMethod("getExecutableItem", String.class);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to hook into ExecutableItems API", e);
                return null;
            }
        }

        if (eiGetItemMethod == null || eiManagerInstance == null) return null;

        try {
            Object optionalItem = eiGetItemMethod.invoke(eiManagerInstance, itemId);
            // Returns Optional<ExecutableItemInterface>
            Method isPresentMethod = optionalItem.getClass().getMethod("isPresent");
            if ((boolean) isPresentMethod.invoke(optionalItem)) {
                Method getMethod = optionalItem.getClass().getMethod("get");
                Object eiItem = getMethod.invoke(optionalItem);
                Method buildItemMethod = eiItem.getClass().getMethod("buildItem", int.class, java.util.Optional.class);
                ItemStack result = (ItemStack) buildItemMethod.invoke(eiItem, amount, java.util.Optional.empty());
                return result;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get ExecutableItem: " + itemId, e);
        }
        return null;
    }
}
