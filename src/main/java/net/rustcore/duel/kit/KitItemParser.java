package net.rustcore.duel.kit;

import net.rustcore.duel.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

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
                LOGGER.warning("Unknown item namespace: " + namespace);
                yield null;
            }
        };
    }

    private static ItemStack parseVanillaItem(String materialName, int amount) {
        try {
            Material mat = Material.valueOf(materialName.toUpperCase());
            return new ItemStack(mat, amount);
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Unknown material: " + materialName);
            return null;
        }
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
