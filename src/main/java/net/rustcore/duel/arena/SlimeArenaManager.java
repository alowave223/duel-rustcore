package net.rustcore.duel.arena;

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.loaders.SlimeLoader;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.infernalsuite.asp.api.world.SlimeWorldInstance;
import com.infernalsuite.asp.api.world.properties.SlimeProperties;
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap;
import com.infernalsuite.asp.loaders.file.FileLoader;
import net.rustcore.duel.DuelsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages per-duel SlimeWorld instances via Advanced SlimeWorldManager (ASWM).
 *
 * Lifecycle:
 * 1. On plugin enable: initialize ASWM API and file loader.
 * 2. On duel start: read the arena's .slime world from disk, clone it into "duel_{uuid12}", and load it.
 * 3. Between rounds (Bo3/Bo5): same world stays alive, blocks reverted via PlacedBlockTracker.
 * 4. On match end: unload and delete the cloned world.
 */
public class SlimeArenaManager {

    private final DuelsPlugin plugin;
    private AdvancedSlimePaperAPI aswmApi;
    private SlimeLoader loader;
    private boolean available = false;

    /** Active cloned worlds: duelId -> world name */
    private final Map<UUID, String> activeWorlds = new ConcurrentHashMap<>();

    public SlimeArenaManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize ASWM integration. Call from onEnable().
     *
     * @return true if ASWM API and file loader initialized successfully
     */
    public boolean init() {
        try {
            aswmApi = AdvancedSlimePaperAPI.instance();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get ASWM API instance", e);
            return false;
        }

        if (aswmApi == null) {
            plugin.getLogger().severe("ASWM API instance is null — cannot initialize arena system.");
            return false;
        }

        String slimeDir = plugin.getConfig().getString("arenas.slime-directory", "slime_worlds");

        try {
            loader = new FileLoader(new File(slimeDir));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize ASP file loader at '" + slimeDir + "'", e);
            return false;
        }

        available = true;
        plugin.getLogger().info("ASWM arena system initialized. Worlds loaded from: " + slimeDir);
        return true;
    }

    /**
     * Read the arena's SlimeWorld from disk, clone it for a specific duel, and load it.
     *
     * @param duelId  the duel's UUID
     * @param arenaId the arena template ID (must match a .slime file in the slime-directory)
     * @return CompletableFuture that completes with the Bukkit World on the main thread
     */
    public CompletableFuture<World> createDuelWorld(UUID duelId, String arenaId) {
        if (!available) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("ASWM is not available"));
        }

        String worldName = "duel_" + duelId.toString().replace("-", "").substring(0, 12);
        CompletableFuture<World> future = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            try {
                // Read the arena's pre-built world from disk (read-only source)
                SlimePropertyMap props = new SlimePropertyMap();
                props.setValue(SlimeProperties.PVP, true);
                props.setValue(SlimeProperties.ALLOW_ANIMALS, false);
                props.setValue(SlimeProperties.ALLOW_MONSTERS, false);

                SlimeWorld source = aswmApi.readWorld(loader, arenaId, true, props);
                SlimeWorld cloned = source.clone(worldName);

                // Load the cloned world on the main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        SlimeWorldInstance instance = aswmApi.loadWorld(cloned, true);
                        World world = instance.getBukkitWorld();
                        if (world == null) {
                            future.completeExceptionally(
                                    new RuntimeException("Cloned world '" + worldName + "' not found after loading"));
                            return;
                        }
                        activeWorlds.put(duelId, worldName);
                        plugin.getLogger().info("Created duel world: " + worldName + " (from arena: " + arenaId + ")");
                        future.complete(world);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to load arena world '" + arenaId + "' for duel " + duelId, e);
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * Check if a .slime world file exists for the given arena ID.
     */
    public boolean worldExists(String arenaId) {
        try {
            return loader.worldExists(arenaId);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to check world existence for arena '" + arenaId + "'", e);
            return false;
        }
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
        for (UUID duelId : new ArrayList<>(activeWorlds.keySet())) {
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
