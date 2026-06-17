package net.rustcore.duel.command;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.util.CC;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
        if (!player.hasPermission("duels.lobby")) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("no-permission"))));
            return true;
        }

        if (plugin.getDuelManager().isInDuel(player.getUniqueId())) {
            plugin.getDuelManager().forfeitDuel(player);
        }
        plugin.getDuelManager().getQueue().removePlayer(player.getUniqueId());

        int index = 0;
        if (args.length >= 1) {
            try {
                index = Integer.parseInt(args[0]) - 1;
            } catch (NumberFormatException e) {
                player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                        .append(CC.parse(plugin.getMessage("hub-invalid-number"))));
                return true;
            }
        }

        int count = plugin.getLobbyManager().getHubCount();
        if (index < 0 || index >= count) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("hub-out-of-range"),
                            "{n}", String.valueOf(index + 1),
                            "{max}", String.valueOf(count))));
            return true;
        }

        plugin.getLobbyManager().sendToHub(player, index);
        return true;
    }
}
