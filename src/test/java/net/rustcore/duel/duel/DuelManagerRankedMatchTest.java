package net.rustcore.duel.duel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelManagerRankedMatchTest {

    @Test
    void rankedQueueKeyRoundTripsToRankedMatch() {
        String queueKey = DuelManager.queueKey("nodebuff", true);

        assertEquals("nodebuff:ranked", queueKey);
        assertEquals("nodebuff", DuelManager.modeIdFromQueueKey(queueKey));
        assertTrue(DuelManager.isRankedQueueKey(queueKey));
    }

    @Test
    void unrankedQueueKeyRoundTripsToUnrankedMatch() {
        String queueKey = DuelManager.queueKey("nodebuff", false);

        assertEquals("nodebuff:unranked", queueKey);
        assertEquals("nodebuff", DuelManager.modeIdFromQueueKey(queueKey));
        assertFalse(DuelManager.isRankedQueueKey(queueKey));
    }
}
