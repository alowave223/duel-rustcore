package net.rustcore.duel.listener;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.command.KitLayoutCommand;
import net.rustcore.duel.kit.KitLayout;
import net.rustcore.duel.mode.DuelMode;
import net.rustcore.duel.mode.impl.FixedKitMode;
import net.rustcore.duel.util.CC;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Detects close of the /kitlayout editor and stores the reconstructed
 * {@link KitLayout} for the player/mode.
 */
public class KitLayoutEditorListener implements Listener {

    private final DuelsPlugin plugin;

    public KitLayoutEditorListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    @SuppressWarnings("deprecation")
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        String title;
        try {
            title = CC.strip(event.getView().title());
        } catch (Throwable t) {
            title = event.getView().getTitle();
        }
        if (title == null || !title.startsWith(KitLayoutCommand.EDITOR_TITLE_PREFIX)) return;

        String modeId = title.substring(KitLayoutCommand.EDITOR_TITLE_PREFIX.length()).trim();
        DuelMode mode = plugin.getModeManager().getMode(modeId);
        if (!(mode instanceof FixedKitMode fixed)) return;

        Inventory inv = event.getInventory();
        KitLayout layout = new KitLayout();

        // For each original-slot item declared by the mode, find where it landed in the editor.
        for (int origSlot = 0; origSlot < 9; origSlot++) {
            ItemStack original = fixed.getHotbarItem(origSlot);
            if (original == null) continue;
            int placedAt = origSlot;
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack here = inv.getItem(i);
                if (here != null && here.isSimilar(original)) {
                    placedAt = i;
                    break;
                }
            }
            layout.setRemap(origSlot, placedAt);
        }

        plugin.getKitLayoutManager().setLayout(player.getUniqueId(), fixed.getId(), layout);
        player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("kitlayout-saved"), "{mode}", fixed.getDisplayName())));
    }
}
