package net.rustcore.duel.command;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.util.CC;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LobbyCommand implements CommandExecutor {

    private final DuelsPlugin plugin;

    public LobbyCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CC.parse(plugin.getMessage("only-players")));
            return true;
        }
        if (!player.hasPermission("duels.lobby")) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("no-permission"))));
            return true;
        }

        if (plugin.getDuelManager().isInDuel(player.getUniqueId())) {
            plugin.getDuelManager().forfeitDuel(player);
        }
        plugin.getDuelManager().getQueue().removePlayer(player.getUniqueId());

        String server = plugin.getConfig().getString("lobby.server", "lobby");
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);
        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        return true;
    }
}
