package net.rustcore.duel.kit;

import net.rustcore.duel.DuelsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores per-player, per-mode {@link KitLayout}s and persists them to
 * {@code data/kit_layouts.yml}.
 */
public class KitLayoutManager {

    private final DuelsPlugin plugin;
    private final File file;

    // uuid -> (modeId -> layout)
    private final Map<UUID, Map<String, KitLayout>> layouts = new ConcurrentHashMap<>();

    public KitLayoutManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/kit_layouts.yml");
    }

    public void load() {
        layouts.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yml.getConfigurationSection("players");
        if (section == null) return;
        for (String uidKey : section.getKeys(false)) {
            UUID uid;
            try {
                uid = UUID.fromString(uidKey);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection modes = section.getConfigurationSection(uidKey);
            if (modes == null) continue;
            Map<String, KitLayout> byMode = new HashMap<>();
            for (String modeId : modes.getKeys(false)) {
                ConfigurationSection remap = modes.getConfigurationSection(modeId);
                if (remap == null) continue;
                KitLayout layout = new KitLayout();
                for (String origKey : remap.getKeys(false)) {
                    int orig;
                    try {
                        orig = Integer.parseInt(origKey);
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    int target = remap.getInt(origKey);
                    layout.setRemap(orig, target);
                }
                byMode.put(modeId, layout);
            }
            layouts.put(uid, byMode);
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, KitLayout>> byPlayer : layouts.entrySet()) {
            String uidKey = byPlayer.getKey().toString();
            for (Map.Entry<String, KitLayout> byMode : byPlayer.getValue().entrySet()) {
                String modeId = byMode.getKey();
                KitLayout layout = byMode.getValue();
                for (Map.Entry<Integer, Integer> e : layout.getRaw().entrySet()) {
                    yml.set("players." + uidKey + "." + modeId + "." + e.getKey(), e.getValue());
                }
            }
        }
        try {
            file.getParentFile().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save kit_layouts.yml: " + ex.getMessage());
        }
    }

    /**
     * Get the layout for the given player and mode, or {@code null} if
     * none has been saved.
     */
    public KitLayout getLayout(UUID uid, String modeId) {
        Map<String, KitLayout> byMode = layouts.get(uid);
        if (byMode == null) return null;
        return byMode.get(modeId);
    }

    /**
     * Store the layout for the given player and mode, overwriting any
     * existing entry. Saves asynchronously? No — saves synchronously (small
     * file, infrequent writes).
     */
    public void setLayout(UUID uid, String modeId, KitLayout layout) {
        layouts.computeIfAbsent(uid, k -> new ConcurrentHashMap<>()).put(modeId, layout);
        save();
    }

    /**
     * Remove the layout for the given player/mode, reverting to default.
     */
    public void resetLayout(UUID uid, String modeId) {
        Map<String, KitLayout> byMode = layouts.get(uid);
        if (byMode == null) return;
        if (byMode.remove(modeId) != null) {
            save();
        }
    }
}
