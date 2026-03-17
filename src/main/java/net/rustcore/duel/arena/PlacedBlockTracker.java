package net.rustcore.duel.arena;

import org.bukkit.block.Block;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks blocks placed by players during a duel session.
 * Used to determine which blocks players are allowed to break
 * (only their own placed blocks, not the arena structure).
 * Thread-safe: may be read on main thread and cleared from async restore thread.
 */
public class PlacedBlockTracker {

    // Key: duel UUID -> Set of placed block locations
    private final Map<UUID, Set<Long>> placedBlocks = new ConcurrentHashMap<>();

    public void trackBlock(UUID duelId, Block block) {
        placedBlocks.computeIfAbsent(duelId, k -> ConcurrentHashMap.newKeySet())
                .add(encodeLocation(block));
    }

    public boolean isPlacedBlock(UUID duelId, Block block) {
        Set<Long> blocks = placedBlocks.get(duelId);
        return blocks != null && blocks.contains(encodeLocation(block));
    }

    public void removeBlock(UUID duelId, Block block) {
        Set<Long> blocks = placedBlocks.get(duelId);
        if (blocks != null) {
            blocks.remove(encodeLocation(block));
        }
    }

    public void clearDuel(UUID duelId) {
        placedBlocks.remove(duelId);
    }

    /**
     * Check if the block was placed by any duel and remove it from tracking.
     * Returns true if the block was a player-placed block (should NOT be destroyed by explosion).
     * This is O(active duels) — much cheaper than the O(duels × blocks) pattern in explosion handlers.
     */
    public boolean isPlacedByAnyDuelAndRemove(Block block) {
        long encoded = encodeLocation(block);
        for (Set<Long> blocks : placedBlocks.values()) {
            if (blocks.remove(encoded)) return true;
        }
        return false;
    }

    /**
     * Encode a block location into a single long for fast lookups.
     */
    private long encodeLocation(Block block) {
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | ((long) y & 0xFFF);
    }
}
