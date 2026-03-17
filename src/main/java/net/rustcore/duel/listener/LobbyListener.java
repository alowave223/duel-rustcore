package net.rustcore.duel.listener;

import net.rustcore.duel.DuelsPlugin;
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
import org.bukkit.inventory.ItemStack;

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

        Player player = event.getPlayer();

        // Don't handle lobby items if in a duel
        if (plugin.getDuelManager().isInDuel(player.getUniqueId())) return;

        ItemStack item = event.getItem();
        String action = plugin.getLobbyManager().getLobbyItemAction(item);
        if (action == null || action.isEmpty()) return;

        event.setCancelled(true);

        switch (action) {
            case "open_queue_menu" -> {
                // Execute DeluxeMenus command or handle internally
                // For now, dispatch the command which can be configured in DeluxeMenus
                Bukkit.dispatchCommand(player, "dm open queue_menu");
            }
            case "open_leaderboard_menu" -> {
                Bukkit.dispatchCommand(player, "dm open leaderboard_menu");
            }
            case "open_cosmetics_menu" -> {
                player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                        .append(CC.parse("<gray>Cosmetics coming soon!")));
            }
            case "open_hub_menu" -> {
                Bukkit.dispatchCommand(player, "dm open hub_menu");
            }
            case "open_settings_menu" -> {
                player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                        .append(CC.parse("<gray>Settings coming soon!")));
            }
            case "quit_to_lobby" -> {
                // Send to BungeeCord/Velocity lobby
                // Using plugin messaging channel
                sendToServer(player, "lobby");
            }
            default -> {
                // Unknown action values from config are silently ignored
                // to prevent arbitrary command injection via config editing.
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
            String action = plugin.getLobbyManager().getLobbyItemAction(event.getCurrentItem());
            if (action != null && !action.isEmpty()) {
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
        String action = plugin.getLobbyManager().getLobbyItemAction(event.getItemDrop().getItemStack());
        if (action != null && !action.isEmpty()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHandSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (plugin.getDuelManager().isInDuel(player.getUniqueId())) return;

        // Prevent swapping lobby items
        String action1 = plugin.getLobbyManager().getLobbyItemAction(event.getMainHandItem());
        String action2 = plugin.getLobbyManager().getLobbyItemAction(event.getOffHandItem());
        if ((action1 != null && !action1.isEmpty()) || (action2 != null && !action2.isEmpty())) {
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
