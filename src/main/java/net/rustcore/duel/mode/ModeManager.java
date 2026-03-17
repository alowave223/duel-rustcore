package net.rustcore.duel.mode;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.mode.impl.KitBuilderMode;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry for all duel modes. Loads modes from their respective configs.
 */
public class ModeManager {

    private final DuelsPlugin plugin;
    private final Map<String, DuelMode> modes = new LinkedHashMap<>();

    public ModeManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        modes.clear();

        // Register built-in modes
        KitBuilderMode kitBuilder = new KitBuilderMode(plugin);
        kitBuilder.load();
        if (kitBuilder.isEnabled()) {
            modes.put(kitBuilder.getId(), kitBuilder);
        }

        // Future modes can be registered here
        // e.g., PotPvPMode, SoupMode, ClassicMode, etc.

        plugin.getLogger().info("Loaded " + modes.size() + " duel mode(s)");
    }

    public DuelMode getMode(String id) {
        return modes.get(id);
    }

    public Collection<DuelMode> getAllModes() {
        return modes.values();
    }

    public DuelMode getDefaultMode() {
        return modes.values().stream().findFirst().orElse(null);
    }

    public void reload() {
        for (DuelMode mode : modes.values()) {
            mode.reload();
        }
    }
}
