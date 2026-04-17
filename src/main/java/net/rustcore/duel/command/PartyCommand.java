package net.rustcore.duel.command;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.party.Party;
import net.rustcore.duel.party.PartyManager;
import net.rustcore.duel.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class PartyCommand implements CommandExecutor {

    private final DuelsPlugin plugin;

    public PartyCommand(DuelsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CC.parse(plugin.getMessage("only-players")));
            return true;
        }
        if (!player.hasPermission("duels.party")) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("no-permission"))));
            return true;
        }

        String sub = args.length == 0 ? "info" : args[0].toLowerCase();
        PartyManager pm = plugin.getPartyManager();
        switch (sub) {
            case "invite" -> handleInvite(player, args, pm);
            case "accept" -> handleAccept(player, pm);
            case "decline", "deny" -> handleDecline(player, pm);
            case "leave" -> handleLeave(player, pm);
            case "kick" -> handleKick(player, args, pm);
            case "disband" -> handleDisband(player, pm);
            case "list", "info" -> handleList(player, pm);
            default -> player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("party-usage"))));
        }
        return true;
    }

    private void handleInvite(Player player, String[] args, PartyManager pm) {
        if (args.length < 2) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("party-usage"))));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("player-not-found"))));
            return;
        }
        // Privacy gate
        if (plugin.getSettingsManager() != null) {
            var s = plugin.getSettingsManager().getSettings(target.getUniqueId());
            if (s.getStatus() == net.rustcore.duel.settings.PlayerSettings.Status.DO_NOT_DISTURB) return;
            var vis = s.getWhoCanInviteToParty();
            if (vis == net.rustcore.duel.settings.PlayerSettings.Visibility.NOBODY) {
                player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                        .append(CC.parse(plugin.getMessage("party-invite-blocked"), "{player}", target.getName())));
                return;
            }
            if (vis == net.rustcore.duel.settings.PlayerSettings.Visibility.FRIENDS_ONLY
                    && !plugin.getFriendManager().isFriend(player.getUniqueId(), target.getUniqueId())) {
                player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                        .append(CC.parse(plugin.getMessage("party-invite-blocked"), "{player}", target.getName())));
                return;
            }
        }
        if (!pm.invite(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("party-invite-failed"))));
            return;
        }
        player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("party-invite-sent"), "{player}", target.getName())));
        target.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("party-invite-received"), "{player}", player.getName())));
    }

    private void handleAccept(Player player, PartyManager pm) {
        if (!pm.acceptInvite(player.getUniqueId())) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("party-no-invite"))));
            return;
        }
        Party party = pm.getParty(player.getUniqueId());
        for (UUID uid : party.getMembers()) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) {
                p.sendMessage(CC.parse(plugin.getMessage("prefix"))
                        .append(CC.parse(plugin.getMessage("party-joined"), "{player}", player.getName())));
            }
        }
    }

    private void handleDecline(Player player, PartyManager pm) {
        var inv = pm.consumeInvite(player.getUniqueId());
        if (inv == null) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("party-no-invite"))));
            return;
        }
        player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("party-declined"))));
    }

    private void handleLeave(Player player, PartyManager pm) {
        if (!pm.leaveParty(player.getUniqueId())) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("party-not-in-party"))));
            return;
        }
        player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("party-left"))));
    }

    private void handleKick(Player player, String[] args, PartyManager pm) {
        if (args.length < 2) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("party-usage"))));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getName() == null) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("player-not-found"))));
            return;
        }
        if (!pm.kickMember(player.getUniqueId(), target.getUniqueId())) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("party-kick-failed"))));
            return;
        }
        player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("party-kicked"), "{player}", target.getName())));
        Player tp = Bukkit.getPlayer(target.getUniqueId());
        if (tp != null) {
            tp.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("party-you-kicked"))));
        }
    }

    private void handleDisband(Player player, PartyManager pm) {
        Party party = pm.getParty(player.getUniqueId());
        if (party == null || !party.isLeader(player.getUniqueId())) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("party-not-leader"))));
            return;
        }
        for (UUID uid : party.getMembers()) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) {
                p.sendMessage(CC.parse(plugin.getMessage("prefix"))
                        .append(CC.parse(plugin.getMessage("party-disbanded"))));
            }
        }
        pm.disbandParty(player.getUniqueId());
    }

    private void handleList(Player player, PartyManager pm) {
        Party party = pm.getParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("party-not-in-party"))));
            return;
        }
        player.sendMessage(CC.parse(plugin.getMessage("party-list-header"),
                "{count}", String.valueOf(party.getSize())));
        for (UUID uid : party.getMembers()) {
            Player p = Bukkit.getPlayer(uid);
            String name = p != null ? p.getName() : Bukkit.getOfflinePlayer(uid).getName();
            if (name == null) name = uid.toString().substring(0, 8);
            boolean leader = party.isLeader(uid);
            player.sendMessage(CC.parse(plugin.getMessage(leader ? "party-list-entry-leader" : "party-list-entry"),
                    "{player}", name));
        }
    }
}
