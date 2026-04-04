package net.rustcore.duel.command;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.util.CC;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

public class HubCommand implements CommandExecutor {

    private final DuelsPlugin plugin;

    public HubCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CC.parse(plugin.getMessage("only-players")));
            return true;
        }

        // If in a duel, forfeit first
        if (plugin.getDuelManager().isInDuel(player.getUniqueId())) {
            plugin.getDuelManager().forfeitDuel(player);
        }

        // Dequeue if queued
        plugin.getDuelManager().getQueue().removePlayer(player.getUniqueId());

        String server = plugin.getConfig().getString("hub.server", "lobby");
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);
        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());

        return true;
    }
}
