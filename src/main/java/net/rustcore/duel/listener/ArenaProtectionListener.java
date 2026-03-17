package net.rustcore.duel.listener;

import net.rustcore.duel.DuelsPlugin;
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

        PlacedBlockTracker tracker = plugin.getArenaManager().getBlockTracker();
        if (!tracker.isPlacedBlock(duel.getId(), event.getBlock())) {
            event.setCancelled(true);
            return;
        }

        tracker.removeBlock(duel.getId(), event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        PlacedBlockTracker tracker = plugin.getArenaManager().getBlockTracker();
        // O(blocks × duels) → O(blocks): single tracker scan per block
        event.blockList().removeIf(block -> !tracker.isPlacedByAnyDuelAndRemove(block));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        PlacedBlockTracker tracker = plugin.getArenaManager().getBlockTracker();
        event.blockList().removeIf(block -> !tracker.isPlacedByAnyDuelAndRemove(block));
    }
}
