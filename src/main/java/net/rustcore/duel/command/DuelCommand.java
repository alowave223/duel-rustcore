package net.rustcore.duel.command;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.duel.Duel;
import net.rustcore.duel.duel.DuelState;
import net.rustcore.duel.mode.DuelMode;
import net.rustcore.duel.mode.impl.KitBuilderMode;
import net.rustcore.duel.stats.PlayerStats;
import net.rustcore.duel.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.*;
import java.util.stream.Collectors;

public class DuelCommand implements TabExecutor {

    private final DuelsPlugin plugin;

    public DuelCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        return switch (sub) {
            case "queue", "q" -> handleQueue(sender, args);
            case "leave" -> handleLeave(sender);
            case "challenge", "duel" -> handleChallenge(sender, args);
            case "accept" -> handleAccept(sender);
            case "decline", "deny" -> handleDecline(sender);
            case "stats" -> handleStats(sender, args);
            case "reload" -> handleReload(sender);
            case "forcestart" -> handleForceStart(sender, args);
            case "ranked" -> handleRankedToggle(sender);
            case "draftmenu" -> handleOpenKitMenu(sender);
            default -> {
                // Treat as a player name for challenge
                if (sender instanceof Player) {
                    yield handleChallenge(sender, new String[] { "challenge", sub,
                            args.length > 1 ? args[1] : defaultModeId() });
                }
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean requirePerm(Player player, String node) {
        if (player.hasPermission(node)) return true;
        player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("no-permission"))));
        return false;
    }

    private boolean handleOpenKitMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CC.parse(plugin.getMessage("only-players")));
            return true;
        }

        if (!requirePerm(player, "duels.draftmenu")) return true;

        Duel duel = plugin.getDuelManager().getDuel(player.getUniqueId());
        if (duel == null) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse("<red>Вы не в дуели!")));
            return true;
        }

        if (!(duel.getMode() instanceof KitBuilderMode kitBuilderMode))
            return true;

        if (duel.getState() != DuelState.DRAFTING) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse("<red>Вы не можете открыть меню сейчас!")));
            return true;
        }

        Inventory draftInv = kitBuilderMode.getKitMenu().getPlayerMenu(player.getUniqueId());

        if (draftInv != null) {
            player.openInventory(draftInv);
            return true;
        } else {
            Set<UUID> readySet = duel.getMeta(KitBuilderMode.META_READY);

            if (readySet == null) {
                return true;
            } else {
                if (!readySet.contains(player.getUniqueId())) {
                    kitBuilderMode.getKitMenu().open(player);
                    return true;
                }
            }

            return true;
        }
    }

    private boolean handleQueue(CommandSender sender, String[] args) {
        // /duel queue leave - leave the queue
        if (args.length > 1 && args[1].equalsIgnoreCase("leave")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(CC.parse(plugin.getMessage("only-players")));
                return true;
            }
            plugin.getDuelManager().dequeuePlayer(player);
            return true;
        }

        // Determine the target player
        // From console: /duel queue <playerName> [mode] [bestOf]
        // From player: /duel queue [mode] [bestOf] (targets self)
        // /duel queue <playerName> [mode] [bestOf] (targets other, requires
        // duels.admin)
        Player target;
        int modeArgIndex;

        if (!(sender instanceof Player senderPlayer)) {
            // Console must provide player name
            if (args.length < 2) {
                sender.sendMessage(CC.parse("<red>Usage: /duel queue <player> [mode] [bestOf]"));
                return true;
            }
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(CC.parse(plugin.getMessage("prefix"))
                        .append(CC.parse(plugin.getMessage("player-not-found"))));
                return true;
            }
            modeArgIndex = 2;
        } else {
            // Player sender - check if first arg is a player name or a mode
            if (args.length > 1) {
                Player possibleTarget = Bukkit.getPlayer(args[1]);
                DuelMode possibleMode = plugin.getModeManager().getMode(args[1]);
                if (possibleTarget != null && possibleMode == null && sender.hasPermission("duels.admin")) {
                    // It's a player name and sender is admin - queue the target
                    target = possibleTarget;
                    modeArgIndex = 2;
                } else {
                    // It's a mode name (or unresolved) - queue self
                    target = senderPlayer;
                    modeArgIndex = 1;
                }
            } else {
                target = senderPlayer;
                modeArgIndex = 1;
            }
        }

        if (!target.hasPermission("duels.queue")) {
            sender.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("no-permission"))));
            return true;
        }

        String modeId = args.length > modeArgIndex ? args[modeArgIndex] : defaultModeId();
        DuelMode mode = plugin.getModeManager().getMode(modeId);
        if (mode == null) {
            sender.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse("<red>Unknown mode: <white><mode_id>. Available: <modes>",
                            "mode_id", modeId,
                            "modes", plugin.getModeManager().getAllModes().stream()
                                    .map(DuelMode::getId).collect(Collectors.joining(", ")))));
            return true;
        }

        int bestOf = mode.getDefaultBestOf();
        int bestOfArgIndex = modeArgIndex + 1;
        if (args.length > bestOfArgIndex) {
            try {
                bestOf = Integer.parseInt(args[bestOfArgIndex]);
                if (bestOf <= 0 || bestOf % 2 == 0) {
                    sender.sendMessage(CC.parse(plugin.getMessage("prefix"))
                            .append(CC.parse(plugin.getMessage("bestof-must-be-odd"))));
                    return true;
                }
                if (!mode.getAvailableBestOf().contains(bestOf)) {
                    sender.sendMessage(CC.parse(plugin.getMessage("prefix"))
                            .append(CC.parse("<red>Invalid best-of. Available: <options>",
                                    "options", mode.getAvailableBestOf().toString())));
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(CC.parse(plugin.getMessage("prefix"))
                        .append(CC.parse("<red>Invalid number: <input>", "input", args[bestOfArgIndex])));
                return true;
            }
        }

        plugin.getDuelManager().queuePlayer(target, modeId, bestOf);
        // Notify sender if different from target
        if (sender instanceof Player sp && !sp.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse("<green>Queued <white>" + target.getName() + "<green> for <white>" + modeId)));
        }
        return true;
    }

    private boolean handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CC.parse(plugin.getMessage("only-players")));
            return true;
        }

        if (!requirePerm(player, "duels.play")) return true;

        if (plugin.getDuelManager().isInDuel(player.getUniqueId())) {
            plugin.getDuelManager().forfeitDuel(player);
        } else {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("not-in-duel"))));
        }
        return true;
    }

    private boolean handleChallenge(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CC.parse(plugin.getMessage("only-players")));
            return true;
        }

        if (!requirePerm(player, "duels.challenge")) return true;

        if (args.length < 2) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse("<red>Usage: /duel challenge <player> [mode] [bestOf]")));
            return true;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("player-not-found"))));
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("cant-duel-self"))));
            return true;
        }

        String modeId = args.length > 2 ? args[2] : defaultModeId();
        DuelMode mode = plugin.getModeManager().getMode(modeId);
        if (mode == null) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse("<red>Unknown mode: <mode_id>", "mode_id", modeId)));
            return true;
        }

        int bestOf = mode.getDefaultBestOf();
        if (args.length > 3) {
            try {
                bestOf = Integer.parseInt(args[3]);
                if (bestOf <= 0) {
                    player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                            .append(CC.parse(plugin.getMessage("bestof-must-be-odd"))));
                    return true;
                }
                if (bestOf % 2 == 0) {
                    player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                            .append(CC.parse(plugin.getMessage("bestof-must-be-odd"))));
                    return true;
                }
                if (!mode.getAvailableBestOf().contains(bestOf)) {
                    player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                            .append(CC.parse("<red>Invalid best-of. Available: <options>",
                                    "options", mode.getAvailableBestOf().toString())));
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                        .append(CC.parse("<red>Invalid number: <input>", "input", args[3])));
                return true;
            }
        }

        plugin.getDuelManager().sendRequest(player, target, modeId, bestOf);
        return true;
    }

    private boolean handleAccept(CommandSender sender) {
        if (!(sender instanceof Player player))
            return true;
        if (!requirePerm(player, "duels.challenge")) return true;
        plugin.getDuelManager().acceptRequest(player);
        return true;
    }

    private boolean handleDecline(CommandSender sender) {
        if (!(sender instanceof Player player))
            return true;
        if (!requirePerm(player, "duels.challenge")) return true;
        plugin.getDuelManager().declineRequest(player);
        return true;
    }

    private boolean handleRankedToggle(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CC.parse(plugin.getMessage("only-players")));
            return true;
        }

        if (!requirePerm(player, "duels.ranked")) return true;

        plugin.getDuelManager().toggleRanked(player.getUniqueId());
        boolean isRanked = plugin.getDuelManager().isRanked(player.getUniqueId());

        if (isRanked) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("ranked-enabled"))));
        } else {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("ranked-disabled"))));
        }
        return true;
    }

    private boolean handleStats(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CC.parse(plugin.getMessage("only-players")));
            return true;
        }

        if (!requirePerm(player, "duels.stats")) return true;

        String modeId = args.length > 1 ? args[1] : defaultModeId();

        // Validate mode exists
        DuelMode mode = plugin.getModeManager().getMode(modeId);
        if (mode == null) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("stats-unknown-mode"),
                            "{mode_id}", modeId,
                            "{modes}", plugin.getModeManager().getAllModes().stream()
                                    .map(DuelMode::getId).collect(Collectors.joining(", ")))));
            return true;
        }

        UUID targetId = player.getUniqueId();

        if (args.length > 2) {
            Player target = Bukkit.getPlayer(args[2]);
            if (target != null) {
                targetId = target.getUniqueId();
            }
        }

        PlayerStats stats = plugin.getStatsManager().getStats(modeId, targetId);
        Player target = Bukkit.getPlayer(targetId);
        String name = target != null ? target.getName() : targetId.toString();

        player.sendMessage(CC.parse(plugin.getMessage("stats-header")));
        player.sendMessage(CC.parse(plugin.getMessage("stats-title"),
                "{player}", name, "{mode}", modeId));
        player.sendMessage(CC.parse(plugin.getMessage("stats-header")));
        player.sendMessage(CC.parse(plugin.getMessage("stats-elo"),
                "{elo}", String.valueOf(stats.getElo())));
        player.sendMessage(CC.parse(plugin.getMessage("stats-wl"),
                "{wins}", String.valueOf(stats.getWins()),
                "{losses}", String.valueOf(stats.getLosses()),
                "{winrate}", String.format("%.1f%%", stats.getWinRate())));
        player.sendMessage(CC.parse(plugin.getMessage("stats-kd"),
                "{kills}", String.valueOf(stats.getKills()),
                "{deaths}", String.valueOf(stats.getDeaths()),
                "{kdr}", String.format("%.2f", stats.getKdr())));
        player.sendMessage(CC.parse(plugin.getMessage("stats-streak"),
                "{streak}", String.valueOf(stats.getWinStreak()),
                "{best}", String.valueOf(stats.getBestWinStreak())));
        player.sendMessage(CC.parse(plugin.getMessage("stats-header")));

        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("duels.admin")) {
            sender.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("no-permission"))));
            return true;
        }

        plugin.reloadConfig();
        plugin.getArenaManager().reload();
        plugin.getModeManager().reload();
        plugin.getLobbyManager().reload();

        sender.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("config-reloaded"))));
        return true;
    }

    private boolean handleForceStart(CommandSender sender, String[] args) {
        if (!sender.hasPermission("duels.admin")) {
            sender.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("no-permission"))));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(CC.parse("<red>Usage: /duel forcestart <player1> <player2> [mode] [bestOf]"));
            return true;
        }

        Player p1 = Bukkit.getPlayer(args[1]);
        Player p2 = Bukkit.getPlayer(args[2]);

        if (p1 == null || p2 == null) {
            sender.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("player-not-found"))));
            return true;
        }

        String modeId = args.length > 3 ? args[3] : defaultModeId();
        DuelMode mode = plugin.getModeManager().getMode(modeId);
        if (mode == null) {
            sender.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse("<red>Unknown mode: <mode_id>", "mode_id", modeId)));
            return true;
        }

        int bestOf = mode.getDefaultBestOf();
        if (args.length > 4) {
            try {
                bestOf = Integer.parseInt(args[4]);
                if (bestOf <= 0) {
                    sender.sendMessage(CC.parse(plugin.getMessage("prefix"))
                            .append(CC.parse(plugin.getMessage("bestof-must-be-odd"))));
                    return true;
                }
                if (bestOf % 2 == 0) {
                    sender.sendMessage(CC.parse(plugin.getMessage("prefix"))
                            .append(CC.parse(plugin.getMessage("bestof-must-be-odd"))));
                    return true;
                }
                if (!mode.getAvailableBestOf().contains(bestOf)) {
                    sender.sendMessage(CC.parse(plugin.getMessage("prefix"))
                            .append(CC.parse("<red>Invalid best-of. Available: <options>",
                                    "options", mode.getAvailableBestOf().toString())));
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(CC.parse(plugin.getMessage("prefix"))
                        .append(CC.parse("<red>Invalid number: <input>", "input", args[4])));
                return true;
            }
        }

        // Remove from queues first to avoid interfering with other matches
        plugin.getDuelManager().getQueue().removePlayer(p1.getUniqueId());
        plugin.getDuelManager().getQueue().removePlayer(p2.getUniqueId());

        // Create duel directly — bypasses queue to avoid matching wrong players
        plugin.getDuelManager().createDuel(modeId, List.of(p1, p2), bestOf);

        sender.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse("<green>Force started duel between " + p1.getName() + " and " + p2.getName())));
        return true;
    }

    private String defaultModeId() {
        DuelMode def = plugin.getModeManager().getDefaultMode();
        return def != null ? def.getId() : "kitbuilder";
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(CC.parse("<gray>═══════════════════════════"));
        sender.sendMessage(CC.parse("<gold><bold>Duels Commands"));
        sender.sendMessage(CC.parse("<gray>═══════════════════════════"));
        sender.sendMessage(CC.parse("<yellow>/duel queue [mode] [bestOf] <gray>- Join the queue"));
        sender.sendMessage(CC.parse("<yellow>/duel queue leave <gray>- Leave the queue"));
        sender.sendMessage(CC.parse("<yellow>/duel leave <gray>- Forfeit your current duel"));
        sender.sendMessage(CC.parse("<yellow>/duel <player> [mode] [bestOf] <gray>- Challenge a player"));
        sender.sendMessage(CC.parse("<yellow>/duel accept <gray>- Accept a challenge"));
        sender.sendMessage(CC.parse("<yellow>/duel decline <gray>- Decline a challenge"));
        sender.sendMessage(CC.parse("<yellow>/duel stats [mode] [player] <gray>- View stats"));
        sender.sendMessage(CC.parse("<yellow>/duel ranked <gray>- Toggle ranked matchmaking"));
        if (sender.hasPermission("duels.admin")) {
            sender.sendMessage(CC.parse("<red>/duel reload <gray>- Reload configuration"));
            sender.sendMessage(CC.parse("<red>/duel forcestart <p1> <p2> [mode] [bo] <gray>- Force start"));
        }
        sender.sendMessage(CC.parse("<gray>═══════════════════════════"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(List.of("queue", "leave", "challenge", "accept", "decline", "stats", "ranked"));
            if (sender.hasPermission("duels.admin")) {
                completions.addAll(List.of("reload", "forcestart"));
            }
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("queue") || sub.equals("q")) {
                completions.add("leave");
                plugin.getModeManager().getAllModes().forEach(m -> completions.add(m.getId()));
                if (sender.hasPermission("duels.admin")) {
                    Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
                }
            }
            if (sub.equals("stats")) {
                plugin.getModeManager().getAllModes().forEach(m -> completions.add(m.getId()));
                Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            }
            if (sub.equals("challenge")) {
                Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            }
            if (sub.equals("forcestart")) {
                Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("queue") || sub.equals("q")) {
                plugin.getModeManager().getAllModes().forEach(m -> completions.add(m.getId()));
            }
            if (sub.equals("challenge")) {
                plugin.getModeManager().getAllModes().forEach(m -> completions.add(m.getId()));
            }
            if (sub.equals("forcestart")) {
                Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            }
            if (sub.equals("stats")) {
                Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            }
        } else if (args.length == 4) {
            String sub = args[0].toLowerCase();
            if (sub.equals("queue") || sub.equals("challenge") || sub.equals("forcestart")) {
                String modeId = args[2].toLowerCase();
                DuelMode mode = plugin.getModeManager().getMode(modeId);
                if (mode != null) {
                    mode.getAvailableBestOf().forEach(bo -> completions.add(String.valueOf(bo)));
                }
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(Collectors.toList());
    }
}
