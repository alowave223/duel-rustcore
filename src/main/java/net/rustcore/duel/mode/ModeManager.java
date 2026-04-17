package net.rustcore.duel.mode;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.mode.impl.FixedKitMode;
import net.rustcore.duel.mode.impl.KitBuilderMode;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
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

        File modesDir = new File(plugin.getDataFolder(), "modes");
        if (!modesDir.exists()) {
            modesDir.mkdirs();
        }

        // Legacy default kitbuilder for back-compat
        KitBuilderMode kitBuilder = new KitBuilderMode(plugin);
        kitBuilder.load();
        if (kitBuilder.isEnabled()) {
            modes.put(kitBuilder.getId(), kitBuilder);
        }

        File[] files = modesDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
                String id = yml.getString("id", f.getName().replace(".yml", ""));
                String type = yml.getString("type", "kitbuilder").toLowerCase();
                try {
                    switch (type) {
                        case "fixed" -> {
                            FixedKitMode m = new FixedKitMode(plugin, id, yml);
                            if (m.isEnabled()) {
                                modes.put(m.getId(), m);
                            }
                        }
                        case "kitbuilder" -> {
                            // already registered as default; per-file kitbuilder variants not supported yet
                        }
                        default -> plugin.getLogger().warning("Unknown mode type '" + type + "' in " + f.getName());
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("Failed to load mode " + id + ": " + ex.getMessage());
                }
            }
        }

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
