package net.rustcore.duel.party;

import java.util.*;

public class Party {

    public static final int MAX_SIZE = 5;

    private final UUID partyId = UUID.randomUUID();
    private UUID leaderId;
    private final Set<UUID> members = new LinkedHashSet<>();

    public Party(UUID leaderId) {
        this.leaderId = leaderId;
        this.members.add(leaderId);
    }

    public UUID getPartyId() { return partyId; }
    public UUID getLeaderId() { return leaderId; }
    public Set<UUID> getMembers() { return Collections.unmodifiableSet(members); }
    public int getSize() { return members.size(); }
    public boolean isFull() { return members.size() >= MAX_SIZE; }
    public boolean isMember(UUID id) { return members.contains(id); }
    public boolean isLeader(UUID id) { return leaderId.equals(id); }

    public boolean addMember(UUID id) {
        if (isFull() || members.contains(id)) return false;
        members.add(id);
        return true;
    }

    public boolean removeMember(UUID id) {
        boolean removed = members.remove(id);
        if (removed && id.equals(leaderId) && !members.isEmpty()) {
            leaderId = members.iterator().next();
        }
        return removed;
    }
}
