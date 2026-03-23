package net.rustcore.duel.arena;

import com.infernalsuite.aswm.api.AdvancedSlimePaperAPI;
import com.infernalsuite.aswm.api.loaders.SlimeLoader;
import com.infernalsuite.aswm.api.world.SlimeWorld;
import com.infernalsuite.aswm.api.world.properties.SlimeProperties;
import com.infernalsuite.aswm.api.world.properties.SlimePropertyMap;
import net.rustcore.duel.DuelsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages per-duel SlimeWorld instances via Advanced SlimeWorldManager (ASWM).
 *
 * Lifecycle:
 * 1. On plugin enable: load the template world as a read-only source.
 * 2. On duel start: clone the template into a new world named "duel_{uuid12}".
 * 3. Between rounds (Bo3/Bo5): same world stays alive, blocks reverted via PlacedBlockTracker.
 * 4. On match end: unload and delete the cloned world.
 */
public class SlimeArenaManager {

    private final DuelsPlugin plugin;
    private AdvancedSlimePaperAPI aswmApi;
    private SlimeLoader loader;
    private SlimeWorld templateWorld;
    private String templateWorldName;
    private boolean available = false;

    /** Active cloned worlds: duelId -> world name */
    private final Map<UUID, String> activeWorlds = new ConcurrentHashMap<>();

    public SlimeArenaManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize ASWM integration. Call from onEnable().
     *
     * @return true if ASWM was found and the template loaded successfully
     */
    public boolean init() {
        // Check if ASWM plugin is present
        if (Bukkit.getPluginManager().getPlugin("SlimeWorldManager") == null) {
            plugin.getLogger().warning("SlimeWorldManager not found — falling back to WorldEdit schematic pasting.");
            return false;
        }

        try {
            aswmApi = AdvancedSlimePaperAPI.instance();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get ASWM API instance", e);
            return false;
        }

        if (aswmApi == null) {
            plugin.getLogger().warning("ASWM API instance is null — falling back to WorldEdit.");
            return false;
        }

        // Get the configured loader and template name
        templateWorldName = plugin.getConfig().getString("arenas.template-world", "duel_template");
        String loaderType = plugin.getConfig().getString("arenas.slime-loader", "file");

        try {
            loader = aswmApi.getLoader(loaderType);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().severe("Unknown ASWM loader type: " + loaderType);
            return false;
        }

        // Load the template world (read-only, not applied to server)
        try {
            SlimePropertyMap props = new SlimePropertyMap();
            props.setValue(SlimeProperties.PVP, true);
            props.setValue(SlimeProperties.ALLOW_ANIMALS, false);
            props.setValue(SlimeProperties.ALLOW_MONSTERS, false);

            templateWorld = aswmApi.readWorld(loader, templateWorldName, true, props);
            plugin.getLogger().info("ASWM template world '" + templateWorldName + "' loaded successfully.");
            available = true;
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to load ASWM template world '" + templateWorldName + "'", e);
            return false;
        }
    }

    /**
     * Clone the template world for a specific duel and load it into the server.
     *
     * @param duelId the duel's UUID
     * @return CompletableFuture that completes with the Bukkit World on the main thread
     */
    public CompletableFuture<World> createDuelWorld(UUID duelId) {
        if (!available) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("ASWM is not available"));
        }

        // Use 12 hex chars to reduce collision risk vs the original 8
        String worldName = "duel_" + duelId.toString().replace("-", "").substring(0, 12);
        CompletableFuture<World> future = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            try {
                SlimeWorld cloned = templateWorld.clone(worldName);

                // Load the world on the main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        aswmApi.loadWorld(cloned, true);
                        World world = Bukkit.getWorld(worldName);
                        if (world == null) {
                            future.completeExceptionally(
                                    new RuntimeException("Cloned world '" + worldName + "' not found after loading"));
                            return;
                        }
                        activeWorlds.put(duelId, worldName);
                        plugin.getLogger().info("Created duel world: " + worldName);
                        future.complete(world);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to clone template for duel " + duelId, e);
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * Unload and delete a duel's cloned world.
     *
     * @param duelId the duel's UUID
     * @return CompletableFuture that completes when the world is fully removed
     */
    public CompletableFuture<Void> destroyDuelWorld(UUID duelId) {
        String worldName = activeWorlds.remove(duelId);
        if (worldName == null) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();

        // Unload must happen on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                // Safety fallback: teleport any remaining players to lobby
                for (var player : world.getPlayers()) {
                    plugin.getLobbyManager().sendToLobby(player);
                }
                Bukkit.unloadWorld(world, false);
            }

            // Delete from storage async
            CompletableFuture.runAsync(() -> {
                try {
                    if (loader.worldExists(worldName)) {
                        loader.deleteWorld(worldName);
                    }
                    plugin.getLogger().info("Destroyed duel world: " + worldName);
                    future.complete(null);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to delete world: " + worldName, e);
                    future.complete(null); // Non-fatal — world will be orphaned
                }
            });
        });

        return future;
    }

    /**
     * Destroy all active duel worlds. Call from onDisable().
     */
    public void destroyAll() {
        for (UUID duelId : activeWorlds.keySet()) {
            String worldName = activeWorlds.remove(duelId);
            if (worldName != null) {
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    for (var player : world.getPlayers()) {
                        plugin.getLobbyManager().sendToLobby(player);
                    }
                    Bukkit.unloadWorld(world, false);
                }
                try {
                    if (loader.worldExists(worldName)) {
                        loader.deleteWorld(worldName);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to delete world on disable: " + worldName, e);
                }
            }
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public String getWorldName(UUID duelId) {
        return activeWorlds.get(duelId);
    }
}
