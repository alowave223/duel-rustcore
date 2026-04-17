package net.rustcore.duel.party;

import net.rustcore.duel.DuelsPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PartyManager {

    private static final long INVITE_TTL_MS = 60_000;

    private final DuelsPlugin plugin;
    private final Map<UUID, Party> playerParty = new ConcurrentHashMap<>();
    private final Map<UUID, Invite> pendingInvites = new ConcurrentHashMap<>();

    public PartyManager(DuelsPlugin plugin) { this.plugin = plugin; }

    public Party getParty(UUID player) { return playerParty.get(player); }
    public boolean isInParty(UUID player) { return playerParty.containsKey(player); }

    public Party createParty(UUID leader) {
        Party existing = playerParty.get(leader);
        if (existing != null) return existing;
        Party p = new Party(leader);
        playerParty.put(leader, p);
        return p;
    }

    public void disbandParty(UUID leader) {
        Party party = playerParty.get(leader);
        if (party == null || !party.isLeader(leader)) return;
        for (UUID member : new ArrayList<>(party.getMembers())) {
            playerParty.remove(member);
        }
    }

    public boolean invite(UUID leader, UUID target) {
        Party party = createParty(leader);
        if (!party.isLeader(leader)) return false;
        if (party.isFull()) return false;
        if (playerParty.containsKey(target)) return false;
        pendingInvites.put(target, new Invite(leader, target, System.currentTimeMillis() + INVITE_TTL_MS));
        return true;
    }

    public Invite consumeInvite(UUID target) {
        Invite inv = pendingInvites.remove(target);
        if (inv == null || System.currentTimeMillis() > inv.expiresAtMs) return null;
        return inv;
    }

    public boolean acceptInvite(UUID target) {
        Invite inv = consumeInvite(target);
        if (inv == null) return false;
        Party party = playerParty.get(inv.leaderId);
        if (party == null || party.isFull()) return false;
        party.addMember(target);
        playerParty.put(target, party);
        return true;
    }

    public boolean leaveParty(UUID member) {
        Party party = playerParty.remove(member);
        if (party == null) return false;
        party.removeMember(member);
        if (party.getSize() <= 1) {
            // Last player left → dissolve the party
            for (UUID u : new ArrayList<>(party.getMembers())) {
                playerParty.remove(u);
            }
        }
        return true;
    }

    public boolean kickMember(UUID leader, UUID target) {
        Party party = playerParty.get(leader);
        if (party == null || !party.isLeader(leader) || !party.isMember(target)) return false;
        if (leader.equals(target)) return false;
        party.removeMember(target);
        playerParty.remove(target);
        return true;
    }

    public record Invite(UUID leaderId, UUID targetId, long expiresAtMs) {}
}
