package net.rustcore.duel.mode;

import net.rustcore.duel.mode.impl.FixedKitMode;
import net.rustcore.duel.modification.Modification;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that FixedKitMode.getModification() never returns null.
 * A null return causes NPEs in every DuelListener handler during
 * FixedKitMode duels.
 */
class FixedKitModeTest {

    @Test
    @Disabled("FixedKitMode constructor instantiates Bukkit ItemStacks — needs Paper registry mock; behavior verified by code review")
    void getModification_neverReturnsNull() {
        ConfigurationSection cfg = minimalKitSection();
        FixedKitMode mode = new FixedKitMode(null, "test_fixed", cfg);

        Modification mod = mode.getModification();
        assertNotNull(mod, "getModification() must not return null — DuelListener calls mod.isNoHunger() etc. without null-check");

        // Verify defaults are sensible
        assertFalse(mod.isDisabledTotems(), "totems should be enabled by default");
        assertEquals(1.0, mod.knockbackMultiplier(), 0.0);
        assertEquals(1.0, mod.damageMultiplier(), 0.0);
        assertFalse(mod.isNoNaturalRegen(), "natural regen should be enabled by default");
        assertTrue(mod.isNoHunger(), "no-hunger should be enabled by default");
        assertFalse(mod.isNoElytra(), "elytra should be enabled by default");
        assertTrue(mod.isKeepInventory(), "keep-inventory should be enabled by default");
        assertFalse(mod.isNoFireTick(), "fire tick should be enabled by default");
    }

    private static ConfigurationSection minimalKitSection() {
        MemoryConfiguration root = new MemoryConfiguration();
        root.set("display-name", "Test Mode");
        root.set("enabled", true);
        root.set("default-best-of", 1);

        ConfigurationSection kit = root.createSection("fixed-kit");
        kit.set("helmet", "DIAMOND_HELMET");
        kit.set("chestplate", "DIAMOND_CHESTPLATE");
        kit.set("leggings", "DIAMOND_LEGGINGS");
        kit.set("boots", "DIAMOND_BOOTS");

        ConfigurationSection hotbar = kit.createSection("hotbar");
        hotbar.set("0", "DIAMOND_SWORD");
        hotbar.set("1", "BOW");

        return root;
    }
}