package net.rustcore.duel.duel;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Simple FIFO matchmaking queue. One queue per mode.
 * When 2 players are in the same mode queue, they get matched.
 */
public class DuelQueue {

    // modeId -> queue of player UUIDs
    private final Map<String, Queue<UUID>> queues = new ConcurrentHashMap<>();
    // Player -> modeId they're queued for
    private final Map<UUID, String> playerModes = new ConcurrentHashMap<>();
    // Player -> best-of preference
    private final Map<UUID, Integer> playerBestOf = new ConcurrentHashMap<>();

    /**
     * Add a player to a mode's queue.
     * 
     * @return a matched pair if a match was found, otherwise null
     */
    public QueueMatch addPlayer(UUID playerId, String modeId, int bestOf) {
        // Remove from any existing queue
        removePlayer(playerId);

        playerModes.put(playerId, modeId);
        playerBestOf.put(playerId, bestOf);

        Queue<UUID> queue = queues.computeIfAbsent(modeId, k -> new ConcurrentLinkedQueue<>());

        // Try to find a match
        UUID opponent = queue.poll();
        while (opponent != null) {
            // Verify opponent is still valid
            if (playerModes.containsKey(opponent) && !opponent.equals(playerId)) {
                // Match found
                playerModes.remove(opponent);
                playerModes.remove(playerId);

                // Use the higher best-of preference
                int matchBestOf = Math.max(bestOf, playerBestOf.getOrDefault(opponent, bestOf));
                playerBestOf.remove(opponent);
                playerBestOf.remove(playerId);

                return new QueueMatch(playerId, opponent, modeId, matchBestOf);
            }
            opponent = queue.poll();
        }

        // No match - add to queue
        queue.add(playerId);
        return null;
    }

    /**
     * Remove a player from their queue.
     */
    public boolean removePlayer(UUID playerId) {
        String modeId = playerModes.remove(playerId);
        playerBestOf.remove(playerId);
        if (modeId != null) {
            Queue<UUID> queue = queues.get(modeId);
            if (queue != null) {
                queue.remove(playerId);
            }
            return true;
        }
        return false;
    }

    public boolean isQueued(UUID playerId) {
        return playerModes.containsKey(playerId);
    }

    public String getQueuedMode(UUID playerId) {
        return playerModes.get(playerId);
    }

    public int getQueueSize(String modeId) {
        Queue<UUID> queue = queues.get(modeId);
        return queue != null ? queue.size() : 0;
    }

    public record QueueMatch(UUID player1, UUID player2, String modeId, int bestOf) {
    }
}
