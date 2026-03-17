package net.rustcore.duel.modification;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Immutable gameplay modifiers for a duel mode.
 * Values are read from the mode's .yml configuration.
 */
public record Modification(
        boolean disabledTotems,
        double knockbackMultiplier,
        double damageMultiplier,
        boolean noNaturalRegen,
        boolean noHunger,
        boolean noElytra,
        boolean keepInventory,
        boolean noFireTick
) {
    /** Default modification with safe values. */
    public static final Modification DEFAULTS = new Modification(
            false, 1.0, 1.0, false, true, false, true, false
    );

    public static Modification fromConfig(ConfigurationSection section) {
        if (section == null) return DEFAULTS;
        return new Modification(
                section.getBoolean("disabled-totems", false),
                section.getDouble("knockback-multiplier", 1.0),
                section.getDouble("damage-multiplier", 1.0),
                section.getBoolean("no-natural-regen", false),
                section.getBoolean("no-hunger", true),
                section.getBoolean("no-elytra", false),
                section.getBoolean("keep-inventory", true),
                section.getBoolean("no-fire-tick", false)
        );
    }

    // Convenience aliases to preserve calling convention
    public boolean isDisabledTotems() { return disabledTotems; }
    public boolean isNoNaturalRegen() { return noNaturalRegen; }
    public boolean isNoHunger() { return noHunger; }
    public boolean isNoElytra() { return noElytra; }
    public boolean isKeepInventory() { return keepInventory; }
    public boolean isNoFireTick() { return noFireTick; }
}
