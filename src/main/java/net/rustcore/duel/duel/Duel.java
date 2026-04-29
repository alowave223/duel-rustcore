package net.rustcore.duel.duel;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.arena.ActiveArena;
import net.rustcore.duel.mode.DuelMode;
import net.rustcore.duel.rating.RatingService;
import net.rustcore.duel.util.CC;

/**
 * Represents an active duel between players.
 * Manages rounds, scoring, countdowns, and state transitions.
 */
public class Duel {

    public static final String META_RANKED_MATCH = "ranked-match";

    private final UUID id;
    private final DuelsPlugin plugin;
    private final DuelMode mode;
    private final ActiveArena activeArena;
    private final List<UUID> playerIds;
    private final int bestOf;

    // Scores: playerUUID -> rounds won
    private final Map<UUID, Integer> scores = new HashMap<>();
    private int currentRound = 0;
    private DuelState state = DuelState.PREPARING;
    private BukkitTask countdownTask;
    private BukkitTask timeLimitTask;

    // Round time limit from config
    private final int roundTimeLimit;

    // Generic per-duel metadata for modes to store transient state
    private final Map<String, Object> metadata = new HashMap<>();

    public Duel(DuelsPlugin plugin, DuelMode mode, ActiveArena activeArena, List<UUID> playerIds, int bestOf) {
        this.id = activeArena.getDuelId();
        this.plugin = plugin;
        this.mode = mode;
        this.activeArena = activeArena;
        this.playerIds = new ArrayList<>(playerIds);
        this.bestOf = bestOf;
        this.roundTimeLimit = plugin.getConfig().getInt("duel.round-time-limit", 300);

        for (UUID pid : playerIds) {
            scores.put(pid, 0);
        }
    }

    public UUID getId() {
        return id;
    }

    public DuelMode getMode() {
        return mode;
    }

    public ActiveArena getActiveArena() {
        return activeArena;
    }

    public int getBestOf() {
        return bestOf;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public DuelState getState() {
        return state;
    }

    public Map<UUID, Integer> getScores() {
        return Collections.unmodifiableMap(scores);
    }

    public List<Player> getPlayers() {
        List<Player> players = new ArrayList<>();
        for (UUID pid : playerIds) {
            Player p = Bukkit.getPlayer(pid);
            if (p != null && p.isOnline())
                players.add(p);
        }
        return players;
    }

    public List<UUID> getPlayerIds() {
        return Collections.unmodifiableList(playerIds);
    }

    @SuppressWarnings("unchecked")
    public <T> T getMeta(String key) {
        return (T) metadata.get(key);
    }

    public void setMeta(String key, Object value) {
        metadata.put(key, value);
    }

    public void removeMeta(String key) {
        metadata.remove(key);
    }

    public boolean isRankedMatch() {
        return Boolean.TRUE.equals(getMeta(META_RANKED_MATCH));
    }

    public boolean isParticipant(UUID playerId) {
        return playerIds.contains(playerId);
    }

    /**
     * Start the next round. Teleports players, sets up mode, opens kit menus.
     */
    public void startNextRound() {
        currentRound++;
        state = DuelState.DRAFTING;

        List<Player> players = getPlayers();
        // Pick one random spawn per team for this round
        List<org.bukkit.Location> spawns = activeArena.pickSpawns(ThreadLocalRandom.current());

        for (int i = 0; i < players.size() && i < spawns.size(); i++) {
            Player player = players.get(i);
            // Face toward the opponent's spawn
            org.bukkit.Location otherSpawn = spawns.get(i == 0 ? 1 : 0);
            org.bukkit.Location spawn = spawns.get(i).clone();
            spawn.setDirection(otherSpawn.toVector().subtract(spawn.toVector()));

            player.teleport(spawn);

            player.setFoodLevel(20);
            player.setSaturation(20f);
            player.setGameMode(GameMode.SURVIVAL);

            // Delay flight reset by 1 tick so the client has time to process the
            // teleport before we change flight state — prevents flying kick on rejoin.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.setAllowFlight(false);
                player.setFlying(false);
            }, 2L);

            // Let the mode set up the player (equip defaults, open kit menu, etc.)
            mode.onRoundSetup(this, player);
        }

        broadcast(CC.parse(plugin.getMessage("round-draft"), "{round}", String.valueOf(currentRound)));
    }

    /**
     * Start the countdown before a round begins.
     */
    public void startCountdown() {
        state = DuelState.COUNTDOWN;
        int countdownSeconds = plugin.getConfig().getInt("duel.countdown-seconds", 5);

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int seconds = countdownSeconds;

            @Override
            public void run() {
                if (seconds <= 0) {
                    countdownTask.cancel();
                    beginRound();
                    return;
                }

                for (Player player : getPlayers()) {
                    player.showTitle(Title.title(
                            CC.parse("<gold><bold>" + seconds),
                            Component.empty(),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(200))));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                }

                seconds--;
            }
        }, 0L, 20L);
    }

    /**
     * Begin the round (after countdown).
     */
    private void beginRound() {
        state = DuelState.ACTIVE;

        for (Player player : getPlayers()) {
            player.showTitle(Title.title(
                    CC.parse("<green><bold>FIGHT!"),
                    Component.empty(),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ofMillis(500))));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.2f);
        }

        mode.onRoundStart(this);

        // Start round time limit
        if (roundTimeLimit > 0) {
            timeLimitTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (state == DuelState.ACTIVE) {
                    broadcast(CC.parse(plugin.getMessage("duel-timeout")));
                    endRound(null); // Draw - no winner
                }
            }, roundTimeLimit * 20L);
        }
    }

    /**
     * Called when a player dies. Checks if the round should end.
     */
    public void handleDeath(Player dead, Player killer) {
        if (state != DuelState.ACTIVE)
            return;

        boolean countsAsLoss = mode.onPlayerDeath(this, dead, killer);
        if (!countsAsLoss)
            return;

        // The killer wins this round
        UUID winnerId = killer != null ? killer.getUniqueId() : null;
        if (winnerId == null) {
            // Died to environment - opponent wins
            for (UUID pid : playerIds) {
                if (!pid.equals(dead.getUniqueId())) {
                    winnerId = pid;
                    break;
                }
            }
        }

        endRound(winnerId);
    }

    /**
     * End the current round.
     * 
     * @param roundWinner UUID of the round winner, or null for a draw
     */
    private void endRound(UUID roundWinner) {
        if (state != DuelState.ACTIVE)
            return;
        state = DuelState.ROUND_ENDING;

        if (timeLimitTask != null) {
            timeLimitTask.cancel();
            timeLimitTask = null;
        }

        if (roundWinner != null) {
            scores.merge(roundWinner, 1, (a, b) -> a + b);
            Player winner = Bukkit.getPlayer(roundWinner);
            String winnerName = winner != null ? winner.getName() : "Unknown";
            broadcast(CC.parse(plugin.getMessage("round-win"),
                    "{player}", winnerName,
                    "{round}", String.valueOf(currentRound)));
        } else {
            broadcast(CC.parse(plugin.getMessage("round-draw"), "{round}", String.valueOf(currentRound)));
        }

        // Show scores
        broadcastScores();

        // Check if someone won the match
        int roundsToWin = (bestOf / 2) + 1;
        UUID matchWinner = null;
        for (Map.Entry<UUID, Integer> entry : scores.entrySet()) {
            if (entry.getValue() >= roundsToWin) {
                matchWinner = entry.getKey();
                break;
            }
        }

        int roundEndDelay = plugin.getConfig().getInt("duel.round-end-delay", 3);

        if (matchWinner != null) {
            // Duel is over
            UUID finalMatchWinner = matchWinner;
            Bukkit.getScheduler().runTaskLater(plugin, () -> endDuel(finalMatchWinner), roundEndDelay * 20L);
        } else if (currentRound >= bestOf) {
            // All rounds played, determine winner by score or declare draw
            UUID bestPlayer = null;
            int bestScore = 0;
            boolean tie = false;
            for (Map.Entry<UUID, Integer> entry : scores.entrySet()) {
                if (entry.getValue() > bestScore) {
                    bestScore = entry.getValue();
                    bestPlayer = entry.getKey();
                    tie = false;
                } else if (entry.getValue() == bestScore) {
                    tie = true;
                }
            }
            UUID finalWinner = tie ? null : bestPlayer;
            Bukkit.getScheduler().runTaskLater(plugin, () -> endDuel(finalWinner), roundEndDelay * 20L);

            getPlayers().forEach(p -> p.setGameMode(GameMode.ADVENTURE));
        } else {
            // More rounds to play - restore arena and start next round
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getArenaManager().restoreArena(activeArena).thenRun(() -> {
                    Bukkit.getScheduler().runTask(plugin, this::startNextRound);
                });
            }, roundEndDelay * 20L);
        }
    }

    /**
     * End the entire duel.
     */
    private void endDuel(UUID winnerId) {
        state = DuelState.ENDED;

        // Immediately prevent post-match combat
        for (Player player : getPlayers()) {
            player.setGameMode(GameMode.ADVENTURE);
        }

        if (winnerId != null) {
            Player winner = Bukkit.getPlayer(winnerId);
            String winnerName = winner != null ? winner.getName() : "Unknown";

            UUID loserId = playerIds.stream()
                    .filter(p -> !p.equals(winnerId))
                    .findFirst().orElse(null);
            int wins = scores.getOrDefault(winnerId, 0);
            int losses = loserId != null ? scores.getOrDefault(loserId, 0) : 0;

            broadcast(CC.parse(plugin.getMessage("duel-win"),
                    "{player}", winnerName,
                    "{wins}", String.valueOf(wins),
                    "{losses}", String.valueOf(losses)));

            boolean isRanked = isRankedMatch();

            if (isRanked)
                plugin.getStatsManager().recordResult(mode.getId(), winnerId, loserId);
        } else {
            broadcast(CC.parse(plugin.getMessage("duel-draw")));
        }

        mode.onDuelEnd(this);

        RatingService ratingService = plugin.getRatingService();
        // Rating updates only for decisive results; draws (winnerId==null) and unranked duels are excluded.
        if (winnerId != null && isRankedMatch() && ratingService != null && ratingService.isEnabled()) {
            List<RatingService.TeamOutcome> outcomes = new ArrayList<>();
            for (UUID pid : playerIds) {
                int rank = pid.equals(winnerId) ? 0 : 1;
                outcomes.add(new RatingService.TeamOutcome(rank, List.of(pid)));
            }
            ratingService.recordMatch(mode.getId(), outcomes);
        }

        // Teleport players to lobby FIRST, then destroy the arena
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : getPlayers()) {
                // Remove active ender pearls to prevent post-duel teleports
                for (EnderPearl pearl : player.getEnderPearls()) {
                    pearl.remove();
                }
                // Always send to lobby (fixed: previously skipped if pearls existed)
                plugin.getLobbyManager().sendToHub(player);
            }

            plugin.getDuelManager().removeDuel(id);

            // Now safe to destroy the arena world (all players are in lobby)
            plugin.getArenaManager().deallocateArena(activeArena);
        }, 60L); // 3 seconds after end message
    }

    private void broadcast(Component message) {
        Component prefixed = CC.parse(plugin.getMessage("prefix")).append(message);
        for (Player player : getPlayers()) {
            player.sendMessage(prefixed);
        }
    }

    private void broadcastScores() {
        String prefix = plugin.getMessage("scores_prefix");
        String playerFormat = plugin.getMessage("player_score_format");
        String separator = plugin.getMessage("scores_separator");
        String unknownPlayer = plugin.getMessage("unknown-player");

        String joinedScores = playerIds.stream()
                .<String>map(pid -> { // <--- Explicitly tell the map to return a String
                    Player p = Bukkit.getPlayer(pid);
                    String name = (p != null) ? p.getName() : unknownPlayer;
                    int score = scores.getOrDefault(pid, 0);

                    return playerFormat
                            .replace("<player>", name)
                            .replace("<score>", String.valueOf(score));
                })
                .collect(Collectors.joining(separator));

        broadcast(CC.parse(prefix + joinedScores));
    }

    /**
     * Force end the duel (e.g., player disconnect).
     */
    public void forceEnd(UUID disconnectedPlayer) {
        if (state == DuelState.ENDED)
            return;

        if (countdownTask != null)
            countdownTask.cancel();
        if (timeLimitTask != null)
            timeLimitTask.cancel();

        UUID winnerId = playerIds.stream()
                .filter(p -> !p.equals(disconnectedPlayer))
                .findFirst().orElse(null);

        state = DuelState.ENDED;
        mode.onDuelEnd(this);

        // Remove active ender pearls from all online players
        for (Player player : getPlayers()) {
            for (EnderPearl pearl : player.getEnderPearls()) {
                pearl.remove();
            }
        }

        if (winnerId != null && disconnectedPlayer != null) {
            Player winner = Bukkit.getPlayer(winnerId);

            boolean isRanked = isRankedMatch();

            if (winner != null) {
                winner.sendMessage(CC.parse(plugin.getMessage("prefix"))
                        .append(CC.parse(plugin.getMessage("opponent-disconnected"))));
                if (isRanked)
                    plugin.getStatsManager().recordResult(mode.getId(), winnerId, disconnectedPlayer);
            }

            RatingService ratingService = plugin.getRatingService();
            // Disconnect counts as a decisive result — rate it the same as a normal finish.
            if (isRankedMatch() && ratingService != null && ratingService.isEnabled()) {
                List<RatingService.TeamOutcome> outcomes = new ArrayList<>();
                for (UUID pid : playerIds) {
                    int rank = pid.equals(winnerId) ? 0 : 1;
                    outcomes.add(new RatingService.TeamOutcome(rank, List.of(pid)));
                }
                ratingService.recordMatch(mode.getId(), outcomes);
            }
        }

        // Teleport ALL online participants to lobby before destroying arena
        for (Player player : getPlayers()) {
            plugin.getLobbyManager().sendToHub(player);
        }

        // Then destroy the arena
        plugin.getDuelManager().removeDuel(id);
        plugin.getArenaManager().deallocateArena(activeArena);
    }
}
