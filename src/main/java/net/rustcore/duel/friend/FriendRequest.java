package net.rustcore.duel.friend;

import java.util.UUID;

public record FriendRequest(UUID senderId, UUID targetId, long expiresAtMs) {
    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAtMs;
    }
}
