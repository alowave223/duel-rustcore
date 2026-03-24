package net.rustcore.duel.command;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.mode.DuelMode;
import net.rustcore.duel.stats.PlayerStats;
import net.rustcore.duel.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

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
            case "leave", "dequeue" -> handleLeave(sender);
            case "challenge", "duel" -> handleChallenge(sender, args);
            case "accept" -> handleAccept(sender);
            case "decline", "deny" -> handleDecline(sender);
            case "stats" -> handleStats(sender, args);
            case "reload" -> handleReload(sender);
            case "forcestart" -> handleForceStart(sender, args);
            default -> {
                // Treat as a player name for challenge
                if (sender instanceof Player) {
                    yield handleChallenge(sender, new String[]{"challenge", sub,
                            args.length > 1 ? args[1] : defaultModeId()});
                }
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean handleQueue(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CC.parse(plugin.getMessage("only-players")));
            return true;
        }

        if (!player.hasPermission("duels.queue")) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("no-permission"))));
            return true;
        }

        String modeId = args.length > 1 ? args[1] : defaultModeId();
        DuelMode mode = plugin.getModeManager().getMode(modeId);
        if (mode == null) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse("<red>Unknown mode: <white><mode_id>. Available: <modes>",
                            "mode_id", modeId,
                            "modes", plugin.getModeManager().getAllModes().stream()
                                    .map(DuelMode::getId).collect(Collectors.joining(", ")))));
            return true;
        }

        int bestOf = mode.getDefaultBestOf();
        if (args.length > 2) {
            try {
                bestOf = Integer.parseInt(args[2]);
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
                        .append(CC.parse("<red>Invalid number: <input>", "input", args[2])));
                return true;
            }
        }

        plugin.getDuelManager().queuePlayer(player, modeId, bestOf);
        return true;
    }

    private boolean handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) return true;
        plugin.getDuelManager().dequeuePlayer(player);
        return true;
    }

    private boolean handleChallenge(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CC.parse(plugin.getMessage("only-players")));
            return true;
        }

        if (!player.hasPermission("duels.queue")) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("no-permission"))));
            return true;
        }

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
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("duels.queue")) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("no-permission"))));
            return true;
        }
        plugin.getDuelManager().acceptRequest(player);
        return true;
    }

    private boolean handleDecline(CommandSender sender) {
        if (!(sender instanceof Player player)) return true;
        plugin.getDuelManager().declineRequest(player);
        return true;
    }

    private boolean handleStats(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return true;

        String modeId = args.length > 1 ? args[1] : defaultModeId();
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

        player.sendMessage(CC.parse("<gray>═══════════════════════════"));
        player.sendMessage(CC.parse("<gold><bold>" + name + "'s Stats <gray>(" + modeId + ")"));
        player.sendMessage(CC.parse("<gray>═══════════════════════════"));
        player.sendMessage(CC.parse("<yellow>ELO: <white>" + stats.getElo()));
        player.sendMessage(CC.parse("<green>Wins: <white>" + stats.getWins()
                + " <gray>| <red>Losses: <white>" + stats.getLosses()
                + " <gray>| <yellow>Win Rate: <white>" + String.format("%.1f%%", stats.getWinRate())));
        player.sendMessage(CC.parse("<aqua>Kills: <white>" + stats.getKills()
                + " <gray>| <red>Deaths: <white>" + stats.getDeaths()
                + " <gray>| <yellow>K/D: <white>" + String.format("%.2f", stats.getKdr())));
        player.sendMessage(CC.parse("<gold>Win Streak: <white>" + stats.getWinStreak()
                + " <gray>| <gold>Best: <white>" + stats.getBestWinStreak()));
        player.sendMessage(CC.parse("<gray>═══════════════════════════"));

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
        sender.sendMessage(CC.parse("<yellow>/duel leave <gray>- Leave the queue"));
        sender.sendMessage(CC.parse("<yellow>/duel <player> [mode] [bestOf] <gray>- Challenge a player"));
        sender.sendMessage(CC.parse("<yellow>/duel accept <gray>- Accept a challenge"));
        sender.sendMessage(CC.parse("<yellow>/duel decline <gray>- Decline a challenge"));
        sender.sendMessage(CC.parse("<yellow>/duel stats [mode] [player] <gray>- View stats"));
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
            completions.addAll(List.of("queue", "leave", "challenge", "accept", "decline", "stats"));
            if (sender.hasPermission("duels.admin")) {
                completions.addAll(List.of("reload", "forcestart"));
            }
            // Also add online player names for quick challenge
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("queue") || sub.equals("stats") || sub.equals("challenge")) {
                plugin.getModeManager().getAllModes().forEach(m -> completions.add(m.getId()));
            }
            if (sub.equals("challenge") || sub.equals("forcestart") || sub.equals("stats")) {
                Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("queue") || sub.equals("challenge")) {
                completions.addAll(plugin.getModeManager().getAllModes().stream()
                    .map(DuelMode::getId)
                    .toList());
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
                String modeId = args[1].toLowerCase();
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
