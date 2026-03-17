package net.rustcore.duel.duel;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.mode.DuelMode;
import net.rustcore.duel.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * Manages all active duels, the matchmaking queue, and duel requests.
 */
public class DuelManager {

    private final DuelsPlugin plugin;
    private final DuelQueue queue = new DuelQueue();

    // Active duels: duelId -> Duel
    private final Map<UUID, Duel> activeDuels = new ConcurrentHashMap<>();
    // Player -> duelId mapping for quick lookup
    private final Map<UUID, UUID> playerDuels = new ConcurrentHashMap<>();
    // Players whose arena is currently being allocated (ALLOCATING state guard)
    private final Set<UUID> allocatingPlayers = ConcurrentHashMap.newKeySet();

    // Duel requests: targetUUID -> DuelRequest
    private final Map<UUID, DuelRequest> pendingRequests = new ConcurrentHashMap<>();

    public DuelManager(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Queue a player for matchmaking.
     */
    public void queuePlayer(Player player, String modeId, int bestOf) {
        if (isInDuel(player.getUniqueId())) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("already-in-duel"))));
            return;
        }

        DuelQueue.QueueMatch match = queue.addPlayer(player.getUniqueId(), modeId, bestOf);

        if (match != null) {
            // Match found - create duel
            Player player1 = Bukkit.getPlayer(match.player1());
            Player player2 = Bukkit.getPlayer(match.player2());

            if (player1 != null && player2 != null) {
                createDuel(match.modeId(), List.of(player1, player2), match.bestOf());
            }
        } else {
            DuelMode mode = plugin.getModeManager().getMode(modeId);
            String modeName = mode != null ? mode.getDisplayName() : modeId;
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("queue-joined"), "{mode}", modeName)));
        }
    }

    /**
     * Remove a player from the queue.
     */
    public void dequeuePlayer(Player player) {
        if (queue.removePlayer(player.getUniqueId())) {
            player.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("queue-left"))));
        }
    }

    /**
     * Send a duel request to another player.
     */
    public void sendRequest(Player sender, Player target, String modeId, int bestOf) {
        if (isInDuel(sender.getUniqueId())) {
            sender.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("already-in-duel"))));
            return;
        }
        if (isInDuel(target.getUniqueId())) {
            sender.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("already-in-duel-target"))));
            return;
        }

        DuelRequest request = new DuelRequest(sender.getUniqueId(), target.getUniqueId(), modeId, bestOf);
        pendingRequests.put(target.getUniqueId(), request);

        sender.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("duel-request-sent"), "{player}", target.getName())));
        target.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("duel-request-received"), "{player}", sender.getName())));

        // Expire after 30 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            DuelRequest pending = pendingRequests.get(target.getUniqueId());
            if (pending != null && pending.equals(request)) {
                pendingRequests.remove(target.getUniqueId());
                Player s = Bukkit.getPlayer(sender.getUniqueId());
                if (s != null) {
                    s.sendMessage(CC.parse(plugin.getMessage("prefix"))
                            .append(CC.parse(plugin.getMessage("duel-expired"))));
                }
            }
        }, 600L);
    }

    /**
     * Accept a pending duel request.
     */
    public void acceptRequest(Player target) {
        DuelRequest request = pendingRequests.remove(target.getUniqueId());
        if (request == null) {
            target.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("no-pending-request"))));
            return;
        }

        Player sender = Bukkit.getPlayer(request.senderId());
        if (sender == null || !sender.isOnline()) {
            target.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("player-not-found"))));
            return;
        }

        // Guard: either player may have entered a duel while the request was pending
        if (isInDuel(sender.getUniqueId())) {
            target.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("already-in-duel-target"))));
            return;
        }
        if (isInDuel(target.getUniqueId())) {
            target.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("already-in-duel"))));
            return;
        }

        // Remove both from queue if queued
        queue.removePlayer(sender.getUniqueId());
        queue.removePlayer(target.getUniqueId());

        sender.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("duel-accepted"))));
        target.sendMessage(CC.parse(plugin.getMessage("prefix"))
                .append(CC.parse(plugin.getMessage("duel-accepted"))));

        createDuel(request.modeId(), List.of(sender, target), request.bestOf());
    }

    /**
     * Decline a pending duel request.
     */
    public void declineRequest(Player target) {
        DuelRequest request = pendingRequests.remove(target.getUniqueId());
        if (request != null) {
            target.sendMessage(CC.parse(plugin.getMessage("prefix"))
                    .append(CC.parse(plugin.getMessage("duel-declined"))));
            Player sender = Bukkit.getPlayer(request.senderId());
            if (sender != null) {
                sender.sendMessage(CC.parse(plugin.getMessage("prefix"))
                        .append(CC.parse(plugin.getMessage("duel-declined"))));
            }
        }
    }

    /**
     * Create and start a new duel.
     */
    public void createDuel(String modeId, List<Player> players, int bestOf) {
        DuelMode mode = plugin.getModeManager().getMode(modeId);
        if (mode == null) {
            for (Player p : players) {
                p.sendMessage(CC.parse(plugin.getMessage("prefix"))
                        .append(CC.parse(plugin.getMessage("invalid-mode"))));
            }
            return;
        }

        if (plugin.getArenaManager().getAllTemplates().isEmpty()) {
            for (Player p : players) {
                p.sendMessage(CC.parse(plugin.getMessage("prefix"))
                        .append(CC.parse(plugin.getMessage("no-arenas"))));
            }
            return;
        }

        List<UUID> playerIds = players.stream().map(Player::getUniqueId).toList();

        // Generate a duel id upfront so we can register players immediately,
        // preventing duplicate requests while the async paste is in progress.
        UUID duelId = UUID.randomUUID();
        for (UUID pid : playerIds) {
            playerDuels.put(pid, duelId);
            // Mark as allocating so listeners can safely block damage/death
            // until the Duel object is fully constructed and registered.
            allocatingPlayers.add(pid);
        }

        plugin.getArenaManager().allocateArena(duelId).thenAccept(activeArena -> {
            // Back on the main thread (allocateArena completes on the main thread).
            // duel.getId() == duelId == activeArena.getDuelId()
            Duel duel = new Duel(plugin, mode, activeArena, playerIds, bestOf);
            activeDuels.put(duelId, duel);
            // Arena is ready — remove allocating guard before starting the round
            for (UUID pid : playerIds) {
                allocatingPlayers.remove(pid);
            }
            duel.startNextRound();
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Failed to allocate arena: " + ex.getMessage());
            for (Player p : players) {
                p.sendMessage(CC.parse(plugin.getMessage("prefix"))
                        .append(CC.parse(plugin.getMessage("arena-prepare-failed"))));
            }
            // Clean up player registrations so they can try again
            for (UUID pid : playerIds) {
                playerDuels.remove(pid);
                allocatingPlayers.remove(pid);
            }
            return null;
        });
    }

    public void removeDuel(UUID duelId) {
        Duel duel = activeDuels.remove(duelId);
        if (duel != null) {
            for (UUID pid : duel.getPlayerIds()) {
                playerDuels.remove(pid);
            }
        }
    }

    public boolean isInDuel(UUID playerId) {
        return playerDuels.containsKey(playerId);
    }

    /** Returns true while the arena is being pasted and the Duel object doesn't exist yet. */
    public boolean isAllocating(UUID playerId) {
        return allocatingPlayers.contains(playerId);
    }

    public Duel getDuel(UUID playerId) {
        UUID duelId = playerDuels.get(playerId);
        return duelId != null ? activeDuels.get(duelId) : null;
    }

    public Duel getDuelById(UUID duelId) {
        return activeDuels.get(duelId);
    }

    public DuelQueue getQueue() { return queue; }

    public Collection<Duel> getActiveDuels() {
        return activeDuels.values();
    }

    /**
     * Handle player disconnect - force end their duel.
     */
    public void handleDisconnect(UUID playerId) {
        queue.removePlayer(playerId);
        Duel duel = getDuel(playerId);
        if (duel != null) {
            duel.forceEnd(playerId);
        }
    }

    public record DuelRequest(UUID senderId, UUID targetId, String modeId, int bestOf) {}
}
