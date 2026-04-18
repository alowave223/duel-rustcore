package net.rustcore.duel.friend;

import net.rustcore.duel.DuelsPlugin;
import net.rustcore.duel.db.dao.FriendsDao;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FriendManager {

    private static final long REQUEST_TTL_MS = 60_000L;

    private final DuelsPlugin plugin;
    private final FriendsDao dao;
    private final Map<UUID, Set<UUID>> friends = new ConcurrentHashMap<>();
    private final Set<UUID> hydrated = ConcurrentHashMap.newKeySet();
    private final Map<UUID, FriendRequest> pendingByTarget = new ConcurrentHashMap<>();

    public FriendManager(DuelsPlugin plugin, FriendsDao dao) {
        this.plugin = plugin;
        this.dao = dao;
    }

    public static FriendManager forTest(FriendsDao dao) { return new FriendManager(null, dao); }

    public void ensureLoaded(UUID uuid) {
        if (hydrated.contains(uuid)) return;
        Set<UUID> set = ConcurrentHashMap.newKeySet();
        set.addAll(dao.loadFriends(uuid));
        friends.put(uuid, set);
        hydrated.add(uuid);
    }

    public Set<UUID> getFriends(UUID uuid) {
        ensureLoaded(uuid);
        return Collections.unmodifiableSet(friends.get(uuid));
    }

    public boolean isFriend(UUID a, UUID b) {
        ensureLoaded(a);
        return friends.get(a).contains(b);
    }

    public boolean addFriend(UUID a, UUID b) {
        ensureLoaded(a); ensureLoaded(b);
        if (!friends.get(a).add(b)) return false;
        friends.get(b).add(a);
        dao.addFriendPair(a, b);
        return true;
    }

    public boolean removeFriend(UUID a, UUID b) {
        ensureLoaded(a); ensureLoaded(b);
        if (!friends.get(a).remove(b)) return false;
        friends.get(b).remove(a);
        dao.removeFriendPair(a, b);
        return true;
    }

    public boolean sendRequest(UUID sender, UUID target) {
        if (sender.equals(target)) return false;
        ensureLoaded(sender);
        if (friends.get(sender).contains(target)) return false;
        pendingByTarget.put(target, new FriendRequest(sender, target, System.currentTimeMillis() + REQUEST_TTL_MS));
        return true;
    }

    public FriendRequest peekPending(UUID target) {
        FriendRequest r = pendingByTarget.get(target);
        if (r != null && r.isExpired()) { pendingByTarget.remove(target); return null; }
        return r;
    }

    public FriendRequest consumePending(UUID target) {
        FriendRequest r = peekPending(target);
        if (r != null) pendingByTarget.remove(target);
        return r;
    }

    public void load() { /* lazy hydration */ }
    public void save() { /* writes synchronous */ }
}
