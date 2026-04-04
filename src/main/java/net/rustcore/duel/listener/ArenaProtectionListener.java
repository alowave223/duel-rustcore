package net.rustcore.duel.listener;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.arena.ActiveArena;
import net.rustcore.duel.arena.CustomPoly2D;
import net.rustcore.duel.arena.PlacedBlockTracker;
import net.rustcore.duel.duel.Duel;
import net.rustcore.duel.duel.DuelState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * Protects arena structures by only allowing players to break blocks
 * they have placed during the current duel. Arena blocks are immutable.
 */
public class ArenaProtectionListener implements Listener {

    private final DuelsPlugin plugin;

    public ArenaProtectionListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Duel duel = plugin.getDuelManager().getDuel(player.getUniqueId());
        if (duel == null) return;

        if (duel.getState() != DuelState.ACTIVE) {
            event.setCancelled(true);
            return;
        }

        // Block placement outside polygon boundary
        CustomPoly2D polygon = duel.getActiveArena().getPolygon();
        if (polygon != null && !polygon.contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }

        PlacedBlockTracker tracker = plugin.getArenaManager().getBlockTracker();
        tracker.trackBlock(duel.getId(), event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Duel duel = plugin.getDuelManager().getDuel(player.getUniqueId());
        if (duel == null) return;

        if (duel.getState() != DuelState.ACTIVE) {
            event.setCancelled(true);
            return;
        }

        // Block break outside polygon boundary is always denied
        CustomPoly2D polygon = duel.getActiveArena().getPolygon();
        if (polygon != null && !polygon.contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }

        // Allow breaking any block inside the polygon (both player-placed and arena-original)
        // Clean up tracker if it was player-placed
        PlacedBlockTracker tracker = plugin.getArenaManager().getBlockTracker();
        tracker.removeBlock(duel.getId(), event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        PlacedBlockTracker tracker = plugin.getArenaManager().getBlockTracker();
        // Allow blocks inside any active arena polygon, remove those outside
        event.blockList().removeIf(block -> {
            boolean insideAnyArena = false;
            for (ActiveArena arena : plugin.getArenaManager().getAllActiveArenas()) {
                CustomPoly2D poly = arena.getPolygon();
                if (poly != null && poly.getWorld() != null
                        && poly.getWorld().equals(block.getWorld())
                        && poly.contains(block.getLocation())) {
                    insideAnyArena = true;
                    break;
                }
            }
            if (!insideAnyArena) return true; // Remove from explosion - outside all arenas
            // Clean up tracker if was player-placed
            tracker.isPlacedByAnyDuelAndRemove(block);
            return false; // Keep in explosion list - inside arena
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        PlacedBlockTracker tracker = plugin.getArenaManager().getBlockTracker();
        event.blockList().removeIf(block -> {
            boolean insideAnyArena = false;
            for (ActiveArena arena : plugin.getArenaManager().getAllActiveArenas()) {
                CustomPoly2D poly = arena.getPolygon();
                if (poly != null && poly.getWorld() != null
                        && poly.getWorld().equals(block.getWorld())
                        && poly.contains(block.getLocation())) {
                    insideAnyArena = true;
                    break;
                }
            }
            if (!insideAnyArena) return true;
            tracker.isPlacedByAnyDuelAndRemove(block);
            return false;
        });
    }
}
