package net.rustcore.duel.arena;

import net.rustcore.duel.DuelsPlugin;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Manages arena templates and allocates/deallocates live arena instances.
 * Each duel gets its own {@link ActiveArena} pasted at a unique world position —
 * multiple duels can use the same schematic template simultaneously.
 *
 * <p>Spawn points are divided into two teams (A and B).
 * Each team may have multiple spawn options; one is chosen randomly per round.
 * Per-arena spawn configuration takes precedence over the global defaults.
 */
public class ArenaManager {

    private final DuelsPlugin plugin;

    /** Arena templates loaded from config (id -> Arena). */
    private final Map<String, Arena> templates = new LinkedHashMap<>();

    /** Currently active arena instances (duelId -> ActiveArena). */
    private final Map<UUID, ActiveArena> activeArenas = new ConcurrentHashMap<>();

    /** Monotonically increasing slot index — gives each duel a unique X offset. */
    private final AtomicInteger slotCounter = new AtomicInteger(0);

    private final PlacedBlockTracker blockTracker = new PlacedBlockTracker();

    private String arenaWorldName;
    private int arenaSpacing;
    private int pasteOriginX, pasteOriginY, pasteOriginZ;

    /** Global default spawn offsets used when an arena has no per-arena section. */
    private final List<SpawnOffset> globalTeamA = new ArrayList<>();
    private final List<SpawnOffset> globalTeamB = new ArrayList<>();

    private record SpawnOffset(double x, double y, double z, float yaw, float pitch) {}

    public ArenaManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        templates.clear();
        globalTeamA.clear();
        globalTeamB.clear();
        // Do NOT reset slotCounter — reloading should not reuse the same world slots
        // while duels that were pasted there may still be active.

        FileConfiguration config = plugin.getConfig();
        arenaWorldName = config.getString("general.arena-world", "duels_world");
        arenaSpacing = config.getInt("arenas.arena-spacing", 200);
        pasteOriginX = config.getInt("arenas.paste-origin.x", 1000);
        pasteOriginY = config.getInt("arenas.paste-origin.y", 80);
        pasteOriginZ = config.getInt("arenas.paste-origin.z", 1000);

        // Load global default spawn points (team-a / team-b keys, or flat legacy list)
        loadGlobalSpawnPoints(config);

        File schemFolder = new File(plugin.getDataFolder(),
                config.getString("arenas.schematics-folder", "schematics"));
        if (!schemFolder.exists()) {
            schemFolder.mkdirs();
            plugin.getLogger().info("Created schematics folder at " + schemFolder.getPath());
            return;
        }

        ConfigurationSection arenaSection = config.getConfigurationSection("arena-list");
        if (arenaSection == null) return;

        for (String arenaId : arenaSection.getKeys(false)) {
            String schemName   = arenaSection.getString(arenaId + ".schematic");
            String displayName = arenaSection.getString(arenaId + ".display-name", arenaId);
            boolean enabled    = arenaSection.getBoolean(arenaId + ".enabled", true);

            if (!enabled) continue;

            File schemFile = new File(schemFolder, schemName);
            if (!schemFile.exists()) {
                plugin.getLogger().warning("Schematic not found for arena '" + arenaId + "': " + schemFile.getPath());
                continue;
            }

            Arena arena = new Arena(arenaId, displayName, schemFile);

            // Per-arena spawn-points override global defaults
            ConfigurationSection spawnSection = arenaSection.getConfigurationSection(arenaId + ".spawn-points");
            if (spawnSection != null) {
                List<SpawnOffset> perA = readTeamOffsets(spawnSection, "team-a");
                List<SpawnOffset> perB = readTeamOffsets(spawnSection, "team-b");

                if (perA.isEmpty() || perB.isEmpty()) {
                    plugin.getLogger().warning("Arena '" + arenaId
                            + "' has spawn-points section but team-a or team-b is empty. "
                            + "Falling back to global defaults.");
                    addOffsetsToArena(arena, globalTeamA, globalTeamB);
                } else {
                    addOffsetsToArena(arena, perA, perB);
                    plugin.getLogger().info("Arena '" + arenaId + "' loaded "
                            + perA.size() + " team-a and " + perB.size() + " team-b spawn(s).");
                }
            } else {
                // No per-arena section — use global defaults
                addOffsetsToArena(arena, globalTeamA, globalTeamB);
            }

            templates.put(arenaId, arena);
            plugin.getLogger().info("Registered arena template: " + arenaId);
        }
    }

    // -------------------------------------------------------------------------
    // Spawn-point config parsing
    // -------------------------------------------------------------------------

    /**
     * Reads the global arenas.spawn-points section.
     * Supports two formats:
     * <ol>
     *   <li>New: {@code team-a:} / {@code team-b:} sub-keys with offset lists.</li>
     *   <li>Legacy: flat list of two offsets (index 0 = team A, index 1 = team B).</li>
     * </ol>
     */
    private void loadGlobalSpawnPoints(FileConfiguration config) {
        ConfigurationSection spawnSection = config.getConfigurationSection("arenas.spawn-points");
        if (spawnSection != null && (spawnSection.contains("team-a") || spawnSection.contains("team-b"))) {
            // New keyed format
            globalTeamA.addAll(readTeamOffsets(spawnSection, "team-a"));
            globalTeamB.addAll(readTeamOffsets(spawnSection, "team-b"));
        } else {
            // Legacy flat list: first entry = team A, second = team B
            List<Map<?, ?>> flatList = config.getMapList("arenas.spawn-points");
            if (flatList.size() >= 1) globalTeamA.add(offsetFromMap(flatList.get(0)));
            if (flatList.size() >= 2) globalTeamB.add(offsetFromMap(flatList.get(1)));
            if (!flatList.isEmpty()) {
                plugin.getLogger().warning("arenas.spawn-points is using the legacy flat format. "
                        + "Please migrate to team-a / team-b sub-keys (see config.yml comments).");
            }
        }

        if (globalTeamA.isEmpty() || globalTeamB.isEmpty()) {
            plugin.getLogger().warning("Global arenas.spawn-points is missing team-a or team-b entries! "
                    + "Arenas without a per-arena override will have no spawn points.");
        } else {
            plugin.getLogger().info("Loaded global spawn offsets: "
                    + globalTeamA.size() + " team-a, " + globalTeamB.size() + " team-b.");
        }
    }

    /** Read a list of SpawnOffset from a {@code team-a} or {@code team-b} key inside a section. */
    private List<SpawnOffset> readTeamOffsets(ConfigurationSection parent, String teamKey) {
        List<SpawnOffset> result = new ArrayList<>();
        List<Map<?, ?>> mapList = parent.getMapList(teamKey);
        for (Map<?, ?> entry : mapList) {
            result.add(offsetFromMap(entry));
        }
        return result;
    }

    private SpawnOffset offsetFromMap(Map<?, ?> map) {
        double x     = toDouble(map.get("x"),     0.5);
        double y     = toDouble(map.get("y"),     64.0);
        double z     = toDouble(map.get("z"),     0.5);
        float  yaw   = (float) toDouble(map.get("yaw"),   0.0);
        float  pitch = (float) toDouble(map.get("pitch"), 0.0);
        return new SpawnOffset(x, y, z, yaw, pitch);
    }

    /**
     * Converts offset lists to dummy relative Locations stored on the Arena template.
     * The World is null here — real Locations are built during allocation.
     */
    private void addOffsetsToArena(Arena arena, List<SpawnOffset> teamA, List<SpawnOffset> teamB) {
        for (SpawnOffset o : teamA) {
            // Store offsets as fake Locations with null world; resolved at allocation time.
            arena.addTeamASpawn(new Location(null, o.x(), o.y(), o.z(), o.yaw(), o.pitch()));
        }
        for (SpawnOffset o : teamB) {
            arena.addTeamBSpawn(new Location(null, o.x(), o.y(), o.z(), o.yaw(), o.pitch()));
        }
    }

    // -------------------------------------------------------------------------
    // Allocation
    // -------------------------------------------------------------------------

    /**
     * Pick a random enabled template, paste it at a fresh world slot, and return
     * a fully-prepared {@link ActiveArena} ready for a duel.
     *
     * Must be called from the main thread; the paste itself runs async.
     *
     * @param duelId UUID of the duel that will own this instance
     * @return CompletableFuture that completes with the ActiveArena on the main thread
     */
    public CompletableFuture<ActiveArena> allocateArena(UUID duelId) {
        return allocateArena(duelId, null);
    }

    /**
     * Like {@link #allocateArena(UUID)} but with an explicit template id.
     * If {@code templateId} is null a random template is chosen.
     */
    public CompletableFuture<ActiveArena> allocateArena(UUID duelId, String templateId) {
        Arena template = (templateId != null)
                ? templates.get(templateId)
                : randomTemplate();

        if (template == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("No arena templates available"));
        }

        if (template.getTeamASpawns().isEmpty() || template.getTeamBSpawns().isEmpty()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Arena '" + template.getId()
                            + "' has no spawn points for one or both teams."));
        }

        // Allocate a slot on the main thread so the counter stays consistent.
        int slot = slotCounter.getAndIncrement();
        int pasteX = pasteOriginX + (slot * arenaSpacing);
        int pasteZ = pasteOriginZ;

        // Resolve the World on the main thread before entering the async block
        World world = Bukkit.getWorld(arenaWorldName);
        if (world == null) {
            return CompletableFuture.failedFuture(
                    new RuntimeException("Arena world not found: " + arenaWorldName));
        }

        CompletableFuture<ActiveArena> result = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            try {
                com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
                BlockVector3 pasteAt = BlockVector3.at(pasteX, pasteOriginY, pasteZ);

                try (ClipboardReader reader = openReader(template)) {
                    Clipboard clipboard = reader.read();
                    try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld);
                         ClipboardHolder holder = new ClipboardHolder(clipboard)) {
                        Operation op = holder.createPaste(editSession)
                                .to(pasteAt)
                                .ignoreAirBlocks(false)
                                .build();
                        Operations.complete(op);
                    }
                }

                // Build the ActiveArena with absolute spawn locations on the main thread.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Location origin = new Location(world, pasteX, pasteOriginY, pasteZ);
                    ActiveArena active = new ActiveArena(template, duelId, origin);

                    for (Location offset : template.getTeamASpawns()) {
                        active.addTeamASpawn(new Location(
                                world,
                                pasteX + offset.getX(),
                                pasteOriginY + offset.getY(),
                                pasteZ + offset.getZ(),
                                offset.getYaw(),
                                offset.getPitch()
                        ));
                    }
                    for (Location offset : template.getTeamBSpawns()) {
                        active.addTeamBSpawn(new Location(
                                world,
                                pasteX + offset.getX(),
                                pasteOriginY + offset.getY(),
                                pasteZ + offset.getZ(),
                                offset.getYaw(),
                                offset.getPitch()
                        ));
                    }

                    activeArenas.put(duelId, active);
                    plugin.getLogger().info("Allocated arena '" + template.getId()
                            + "' slot " + slot + " for duel " + duelId);
                    result.complete(active);
                });

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to allocate arena for duel " + duelId, e);
                result.completeExceptionally(e);
            }
        });

        return result;
    }

    // -------------------------------------------------------------------------
    // Restoration (between rounds)
    // -------------------------------------------------------------------------

    /**
     * Re-paste the schematic for an active arena (inter-round reset).
     * Does NOT deallocate the slot — the duel continues.
     */
    public CompletableFuture<Void> restoreArena(ActiveArena active) {
        UUID duelId = active.getDuelId();

        return CompletableFuture.runAsync(() -> {
            try {
                Location origin = active.getPasteOrigin();
                com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(origin.getWorld());
                BlockVector3 pasteAt = BlockVector3.at(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());

                try (ClipboardReader reader = openReader(active.getTemplate())) {
                    Clipboard clipboard = reader.read();
                    try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld);
                         ClipboardHolder holder = new ClipboardHolder(clipboard)) {
                        Operation op = holder.createPaste(editSession)
                                .to(pasteAt)
                                .ignoreAirBlocks(false)
                                .build();
                        Operations.complete(op);
                    }
                }

                blockTracker.clearDuel(duelId);

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to restore arena for duel " + duelId, e);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Deallocation
    // -------------------------------------------------------------------------

    /**
     * Deallocate the arena after a duel ends.
     */
    public void deallocateArena(ActiveArena active) {
        if (active == null) return;
        activeArenas.remove(active.getDuelId());
        blockTracker.clearDuel(active.getDuelId());
        plugin.getLogger().info("Deallocated arena slot for duel " + active.getDuelId());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Arena randomTemplate() {
        if (templates.isEmpty()) return null;
        List<Arena> list = new ArrayList<>(templates.values());
        return list.get(new Random().nextInt(list.size()));
    }

    private ClipboardReader openReader(Arena arena) throws Exception {
        File file = arena.getSchematicFile();
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) throw new IOException("Unknown schematic format: " + file.getName());
        return format.getReader(new FileInputStream(file));
    }

    private static double toDouble(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        return fallback;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public Arena getTemplate(String id) { return templates.get(id); }
    public Collection<Arena> getAllTemplates() { return templates.values(); }
    public ActiveArena getActiveArena(UUID duelId) { return activeArenas.get(duelId); }
    public PlacedBlockTracker getBlockTracker() { return blockTracker; }
    public String getArenaWorldName() { return arenaWorldName; }

    public void reload() { load(); }
}
