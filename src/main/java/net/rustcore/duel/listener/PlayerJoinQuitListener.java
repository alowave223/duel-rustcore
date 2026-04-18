package net.rustcore.duel.listener;

import net.rustcore.duel.DuelsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

public final class PlayerJoinQuitListener implements Listener {

    private final DuelsPlugin plugin;

    public PlayerJoinQuitListener(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getFriendManager().ensureLoaded(uuid);
            plugin.getSettingsManager().getSettings(uuid);
        });
    }
}
