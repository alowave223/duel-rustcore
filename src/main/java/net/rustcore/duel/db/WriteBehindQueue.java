package net.rustcore.duel.db;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class WriteBehindQueue<T> {

    private final Set<T> dirty = ConcurrentHashMap.newKeySet();
    private final Consumer<Set<T>> flusher;

    public WriteBehindQueue(Consumer<Set<T>> flusher) { this.flusher = flusher; }

    public void markDirty(T item) { dirty.add(item); }

    public void flushNow() {
        if (dirty.isEmpty()) return;
        Set<T> snap = new HashSet<>(dirty);
        dirty.removeAll(snap);
        flusher.accept(snap);
    }
}
