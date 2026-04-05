package net.rustcore.duel.listener;

import java.util.List;
import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.lobby.LobbyManager;
import net.rustcore.duel.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Handles lobby-specific interactions:
 * - Right-click lobby items to trigger actions
 * - Prevent lobby item movement/dropping
 * - Give lobby items on join
 */
public class LobbyListener implements Listener {

    private final DuelsPlugin plugin;

    public LobbyListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        // Prevent double-fire: PlayerInteractEvent fires for both hands
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();

        // Don't handle lobby items if in a duel
        if (plugin.getDuelManager().isInDuel(player.getUniqueId())) return;

        List<LobbyManager.ItemAction> actions = plugin.getLobbyManager()
                .getLobbyItemActions(player.getInventory().getHeldItemSlot());
        if (actions.isEmpty()) return;

        event.setCancelled(true);

        for (LobbyManager.ItemAction act : actions) {
            switch (act.type()) {
                case "player" -> player.performCommand(act.command());
                case "console" -> Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        act.command().replace("{player}", player.getName())
                );
                case "message" -> player.sendMessage(
                        CC.parse(plugin.getMessage("prefix"))
                                .append(CC.parse(act.command()))
                );
                case "bungee" -> sendToServer(player, act.command());
                default -> plugin.getLogger().warning(
                        "Unknown action type '" + act.type() + "' in lobby item"
                );
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Don't lock inventory in duels (handled by other listeners)
        if (plugin.getDuelManager().isInDuel(player.getUniqueId())) return;

        // Prevent moving lobby items
        if (event.getCurrentItem() != null) {
            if (plugin.getLobbyManager().isLobbyItem(event.getCurrentItem())) {
                event.setCancelled(true);
                return;
            }
        }

        // Also prevent clicking on lobby item slots
        if (plugin.getLobbyManager().isLobbySlot(event.getSlot())
                && event.getClickedInventory() == player.getInventory()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        // Don't handle in duels
        if (plugin.getDuelManager().isInDuel(player.getUniqueId())) return;

        // Prevent dropping lobby items
        if (plugin.getLobbyManager().isLobbyItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHandSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (plugin.getDuelManager().isInDuel(player.getUniqueId())) return;

        // Prevent swapping lobby items
        if (plugin.getLobbyManager().isLobbyItem(event.getMainHandItem())
                || plugin.getLobbyManager().isLobbyItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Give lobby items on join (after a tick to ensure world is loaded)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !plugin.getDuelManager().isInDuel(player.getUniqueId())) {
                plugin.getLobbyManager().sendToLobby(player);
            }
        }, 5L);
    }

    /**
     * Send player to another server via plugin messaging.
     */
    private void sendToServer(Player player, String server) {
        try {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(server);
            player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
        } catch (Exception e) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse("<red>Failed to connect to server.")));
        }
    }
}
