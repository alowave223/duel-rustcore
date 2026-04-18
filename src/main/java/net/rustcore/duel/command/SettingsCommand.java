package net.rustcore.duel.command;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.settings.PlayerSettings;
import net.rustcore.duel.util.CC;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SettingsCommand implements CommandExecutor {

    private final DuelsPlugin plugin;

    public SettingsCommand(DuelsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CC.parse(plugin.getMessage("only-players")));
            return true;
        }
        if (!player.hasPermission("duels.settings")) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("no-permission"))));
            return true;
        }

        PlayerSettings s = plugin.getSettingsManager().getSettings(player.getUniqueId());

        if (args.length == 0) {
            sendCurrent(player, s);
            return true;
        }

        String key = args[0].toLowerCase();
        if (args.length < 2) {
            player.sendMessage(CC.parse(plugin.getMessage("settings-usage")));
            return true;
        }
        String value = args[1].toUpperCase();

        try {
            switch (key) {
                case "party" -> s.setWhoCanInviteToParty(PlayerSettings.Visibility.valueOf(value));
                case "challenge" -> s.setWhoCanChallenge(PlayerSettings.Visibility.valueOf(value));
                case "friendrequests" -> s.setAcceptFriendRequests(Boolean.parseBoolean(args[1]));
                case "status" -> s.setStatus(PlayerSettings.Status.valueOf(value));
                default -> {
                    player.sendMessage(CC.parse(plugin.getMessage("settings-usage")));
                    return true;
                }
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage(CC.parse(plugin.getMessage("settings-invalid-value")));
            return true;
        }

        plugin.getSettingsManager().update(player.getUniqueId(), s);
        player.sendMessage(CC.parse(plugin.getMessage("settings-updated")));
        sendCurrent(player, s);
        return true;
    }

    private void sendCurrent(Player player, PlayerSettings s) {
        player.sendMessage(CC.parse(plugin.getMessage("settings-header")));
        player.sendMessage(CC.parse(plugin.getMessage("settings-line-party"),
                "{value}", s.getWhoCanInviteToParty().name()));
        player.sendMessage(CC.parse(plugin.getMessage("settings-line-challenge"),
                "{value}", s.getWhoCanChallenge().name()));
        player.sendMessage(CC.parse(plugin.getMessage("settings-line-friendrequests"),
                "{value}", String.valueOf(s.isAcceptFriendRequests())));
        player.sendMessage(CC.parse(plugin.getMessage("settings-line-status"),
                "{value}", s.getStatus().name()));
        player.sendMessage(CC.parse(plugin.getMessage("settings-footer")));
    }
}
