package net.rustcore.duel.util;

import net.kyori.adventure.text.Component;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material, amount);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder(ItemStack item) {
        this.item = item.clone();
        this.meta = this.item.getItemMeta();
    }

    public ItemBuilder name(String miniMessage) {
        meta.displayName(CC.parse(miniMessage));
        return this;
    }

    public ItemBuilder name(Component component) {
        meta.displayName(component);
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(CC.parse(line));
        }
        meta.lore(lore);
        return this;
    }

    public ItemBuilder addLore(String line) {
        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();
        lore.add(CC.parse(line));
        meta.lore(lore);
        return this;
    }

    public ItemBuilder enchant(Enchantment enchantment, int level) {
        meta.addEnchant(enchantment, level, true);
        return this;
    }

    public ItemBuilder enchantments(Map<String, Integer> enchants) {
        if (enchants == null) return this;
        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            Enchantment ench = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(NamespacedKey.minecraft(entry.getKey().toLowerCase()));
            if (ench != null && entry.getValue() > 0) {
                meta.addEnchant(ench, entry.getValue(), true);
            }
        }
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        meta.addItemFlags(flags);
        return this;
    }

    public ItemBuilder hideFlags() {
        meta.addItemFlags(ItemFlag.values());
        return this;
    }

    public ItemBuilder unbreakable(boolean unbreakable) {
        meta.setUnbreakable(unbreakable);
        return this;
    }

    public ItemBuilder customModelData(int data) {
        if (data > 0) {
            meta.setCustomModelData(data);
        }
        return this;
    }

    public ItemBuilder potionType(String potionTypeName) {
        if (meta instanceof PotionMeta potionMeta && potionTypeName != null) {
            try {
                PotionType type = PotionType.valueOf(potionTypeName.toUpperCase());
                potionMeta.setBasePotionType(type);
            } catch (IllegalArgumentException ignored) {
                // Try lookup with registry
            }
        }
        return this;
    }

    public ItemBuilder pdc(NamespacedKey key, String value) {
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        return this;
    }

    public ItemBuilder pdc(NamespacedKey key, int value) {
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value);
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }
}
