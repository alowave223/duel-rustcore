package net.rustcore.duel.friend;

import net.rustcore.duel.DuelsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FriendManager {

    private static final long REQUEST_TTL_MS = 60_000;

    private final DuelsPlugin plugin;
    private final Map<UUID, Set<UUID>> friends = new ConcurrentHashMap<>();
    private final Map<UUID, FriendRequest> pendingByTarget = new ConcurrentHashMap<>();
    private final File file;

    public FriendManager(DuelsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/friends.yml");
    }

    public void load() {
        friends.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yml.getConfigurationSection("players");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            UUID uid;
            try { uid = UUID.fromString(key); } catch (IllegalArgumentException e) { continue; }
            Set<UUID> set = ConcurrentHashMap.newKeySet();
            for (String other : section.getStringList(key + ".friends")) {
                try { set.add(UUID.fromString(other)); } catch (IllegalArgumentException ignored) {}
            }
            friends.put(uid, set);
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Set<UUID>> e : friends.entrySet()) {
            List<String> list = new ArrayList<>();
            for (UUID f : e.getValue()) list.add(f.toString());
            yml.set("players." + e.getKey() + ".friends", list);
        }
        try {
            file.getParentFile().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save friends.yml: " + ex.getMessage());
        }
    }

    public Set<UUID> getFriends(UUID player) {
        return friends.getOrDefault(player, Set.of());
    }

    public boolean isFriend(UUID a, UUID b) {
        return getFriends(a).contains(b);
    }

    public boolean addFriend(UUID a, UUID b) {
        if (a.equals(b)) return false;
        friends.computeIfAbsent(a, k -> ConcurrentHashMap.newKeySet()).add(b);
        friends.computeIfAbsent(b, k -> ConcurrentHashMap.newKeySet()).add(a);
        save();
        return true;
    }

    public boolean removeFriend(UUID a, UUID b) {
        boolean changed = false;
        Set<UUID> sa = friends.get(a);
        if (sa != null && sa.remove(b)) changed = true;
        Set<UUID> sb = friends.get(b);
        if (sb != null && sb.remove(a)) changed = true;
        if (changed) save();
        return changed;
    }

    public boolean sendRequest(UUID from, UUID to) {
        if (from.equals(to)) return false;
        if (isFriend(from, to)) return false;
        pendingByTarget.put(to, new FriendRequest(from, to, System.currentTimeMillis() + REQUEST_TTL_MS));
        return true;
    }

    public FriendRequest consumePending(UUID target) {
        FriendRequest req = pendingByTarget.remove(target);
        if (req == null || req.isExpired()) return null;
        return req;
    }

    public FriendRequest peekPending(UUID target) {
        FriendRequest req = pendingByTarget.get(target);
        if (req == null) return null;
        if (req.isExpired()) { pendingByTarget.remove(target); return null; }
        return req;
    }
}
