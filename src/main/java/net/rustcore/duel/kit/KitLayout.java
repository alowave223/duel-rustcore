package net.rustcore.duel.kit;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-player slot remap for a fixed-kit mode's hotbar.
 *
 * <p>Original slot (as declared in the mode config) -> target slot (as
 * arranged by the player in the editor). A missing mapping means the item
 * keeps its original slot.</p>
 */
public class KitLayout {

    private final Map<Integer, Integer> slotRemap = new HashMap<>();

    /**
     * Remap an original slot. If no remap is set, returns the original slot.
     */
    public int remapSlot(int original) {
        return slotRemap.getOrDefault(original, original);
    }

    /**
     * Set the remap: item at {@code originalSlot} should be placed at
     * {@code targetSlot} when the kit is given.
     */
    public void setRemap(int originalSlot, int targetSlot) {
        slotRemap.put(originalSlot, targetSlot);
    }

    /**
     * Raw view of the backing map (for serialization).
     */
    public Map<Integer, Integer> getRaw() {
        return slotRemap;
    }

    /**
     * Remove all remaps, reverting to default layout.
     */
    public void clear() {
        slotRemap.clear();
    }
}
