package net.rustcore.duel.listener;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.duel.Duel;
import net.rustcore.duel.duel.DuelState;
import net.rustcore.duel.kit.KitMenu;
import net.rustcore.duel.mode.DuelMode;
import net.rustcore.duel.mode.impl.KitBuilderMode;

import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles inventory click events for the kit drafting menu.
 */
public class KitMenuListener implements Listener {

    private final DuelsPlugin plugin;

    public KitMenuListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        Duel duel = plugin.getDuelManager().getDuel(player.getUniqueId());
        if (duel == null)
            return;

        DuelMode mode = duel.getMode();
        if (!(mode instanceof KitBuilderMode kitBuilderMode))
            return;

        KitMenu kitMenu = kitBuilderMode.getKitMenu();
        Inventory topInv = event.getView().getTopInventory();

        if (!kitMenu.isKitMenu(topInv))
            return;

        if (event.getClickedInventory() == topInv) {
            event.setCancelled(true);

            int slot = event.getSlot();

            String menuType = null;
            if (event.getCurrentItem() != null && event.getCurrentItem().hasItemMeta()) {
                menuType = event.getCurrentItem().getItemMeta()
                        .getPersistentDataContainer()
                        .get(kitMenu.KEY_MENU_TYPE, PersistentDataType.STRING);
            }

            if ("ready".equals(menuType)) {
                kitBuilderMode.onPlayerReady(duel, player);
                return;
            }

            if ("clear".equals(menuType)) {
                kitMenu.clearPlayerPicks(player);
                return;
            }

            if ("info".equals(menuType)) {
                return;
            }

            kitMenu.handleClick(player, slot, topInv);
        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        Duel duel = plugin.getDuelManager().getDuel(player.getUniqueId());
        if (duel == null)
            return;

        DuelMode mode = duel.getMode();
        if (!(mode instanceof KitBuilderMode kitBuilderMode))
            return;

        KitMenu kitMenu = kitBuilderMode.getKitMenu();
        Inventory topInv = event.getView().getTopInventory();

        if (kitMenu.isKitMenu(topInv)) {
            for (int slot : event.getRawSlots()) {
                if (slot < topInv.getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player))
            return;

        InventoryCloseEvent.Reason reason = event.getReason();
        if (reason == InventoryCloseEvent.Reason.OPEN_NEW)
            return;

        Duel duel = plugin.getDuelManager().getDuel(player.getUniqueId());
        if (duel == null)
            return;

        DuelMode mode = duel.getMode();
        if (!(mode instanceof KitBuilderMode kitBuilderMode))
            return;

        KitMenu kitMenu = kitBuilderMode.getKitMenu();
        if (kitMenu.isKitMenu(event.getInventory())) {
            if (duel.getState() == DuelState.DRAFTING && reason != InventoryCloseEvent.Reason.PLUGIN) {
                Set<UUID> readySet = duel.getMeta(KitBuilderMode.META_READY);
                if (readySet != null && readySet.contains(player.getUniqueId()))
                    return;

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Duel currentDuel = plugin.getDuelManager().getDuel(player.getUniqueId());
                    if (currentDuel == null || currentDuel.getState() != DuelState.DRAFTING)
                        return;

                    Inventory current = kitMenu.getPlayerMenu(player.getUniqueId());
                    if (current != null && kitMenu.isKitMenu(current)) {
                        player.openInventory(current);
                    }
                }, 5L);
            } else {
                kitMenu.removeMenu(event.getInventory());
                kitMenu.removePlayer(player.getUniqueId());
            }
        }
    }
}
