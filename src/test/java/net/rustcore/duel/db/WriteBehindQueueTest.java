package net.rustcore.duel.db;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WriteBehindQueueTest {
    @Test
    void flushProcessesDirtyOnce() {
        AtomicInteger flushes = new AtomicInteger();
        Set<UUID> seen = ConcurrentHashMap.newKeySet();
        WriteBehindQueue<UUID> q = new WriteBehindQueue<>(batch -> {
            flushes.incrementAndGet();
            seen.addAll(batch);
        });
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        q.markDirty(a); q.markDirty(b); q.markDirty(a);
        q.flushNow();
        assertEquals(1, flushes.get());
        assertEquals(2, seen.size());
        q.flushNow();
        assertEquals(1, flushes.get());
    }
}
