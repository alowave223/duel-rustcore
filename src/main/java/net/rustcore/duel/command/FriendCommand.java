package net.rustcore.duel.command;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.friend.FriendRequest;
import net.rustcore.duel.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class FriendCommand implements CommandExecutor {

    private final DuelsPlugin plugin;

    public FriendCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CC.parse(plugin.getMessage("only-players")));
            return true;
        }
        if (!player.hasPermission("duels.friends")) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("no-permission"))));
            return true;
        }

        String sub = args.length == 0 ? "list" : args[0].toLowerCase();
        switch (sub) {
            case "add" -> handleAdd(player, args);
            case "remove", "rm" -> handleRemove(player, args);
            case "accept" -> handleAccept(player);
            case "decline", "deny" -> handleDecline(player);
            case "list" -> handleList(player);
            default -> player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("friend-usage"))));
        }
        return true;
    }

    private void handleAdd(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("friend-usage"))));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("player-not-found"))));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) return;
        if (plugin.getFriendManager().isFriend(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("friend-already"), "{player}", target.getName())));
            return;
        }
        // Privacy gate (Task 8 integration - safe no-op before SettingsManager exists; null-check plugin.getSettingsManager() if needed)
        if (plugin.getSettingsManager() != null
                && !plugin.getSettingsManager().getSettings(target.getUniqueId()).isAcceptFriendRequests()) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("friend-blocked"), "{player}", target.getName())));
            return;
        }
        plugin.getFriendManager().sendRequest(player.getUniqueId(), target.getUniqueId());
        player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("friend-request-sent"), "{player}", target.getName())));
        target.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("friend-request-received"), "{player}", player.getName())));
    }

    private void handleRemove(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("friend-usage"))));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getName() == null) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("player-not-found"))));
            return;
        }
        if (!plugin.getFriendManager().removeFriend(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("friend-not-friend"), "{player}", target.getName())));
            return;
        }
        player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("friend-removed"), "{player}", target.getName())));
    }

    private void handleAccept(Player player) {
        FriendRequest req = plugin.getFriendManager().consumePending(player.getUniqueId());
        if (req == null) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("friend-no-request"))));
            return;
        }
        plugin.getFriendManager().addFriend(player.getUniqueId(), req.senderId());
        Player sender = Bukkit.getPlayer(req.senderId());
        String senderName = sender != null ? sender.getName() : Bukkit.getOfflinePlayer(req.senderId()).getName();
        player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("friend-added"), "{player}", senderName != null ? senderName : "?")));
        if (sender != null) {
            sender.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("friend-added"), "{player}", player.getName())));
        }
    }

    private void handleDecline(Player player) {
        FriendRequest req = plugin.getFriendManager().consumePending(player.getUniqueId());
        if (req == null) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("friend-no-request"))));
            return;
        }
        player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("friend-declined"))));
    }

    private void handleList(Player player) {
        var set = plugin.getFriendManager().getFriends(player.getUniqueId());
        player.sendMessage(CC.parse(plugin.getMessage("friend-list-header"),
                "{count}", String.valueOf(set.size())));
        for (UUID uid : set) {
            Player online = Bukkit.getPlayer(uid);
            if (online != null) {
                player.sendMessage(CC.parse(plugin.getMessage("friend-list-entry-online"),
                        "{player}", online.getName()));
            } else {
                OfflinePlayer op = Bukkit.getOfflinePlayer(uid);
                player.sendMessage(CC.parse(plugin.getMessage("friend-list-entry-offline"),
                        "{player}", op.getName() != null ? op.getName() : uid.toString().substring(0, 8)));
            }
        }
    }
}
