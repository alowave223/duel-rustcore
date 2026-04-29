package net.rustcore.duel.rating;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RatingConfigTest {

    @Test
    void secretFingerprintIsStableAndDoesNotExposeSecret() throws Exception {
        String secret = "x".repeat(32);
        RatingConfig cfg = new RatingConfig(true, "http://127.0.0.1:8088", secret, 2000, 5000);

        byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        String expected = HexFormat.of().formatHex(digest).substring(0, 12);

        assertEquals(expected, cfg.sharedSecretFingerprint());
        assertFalse(cfg.sharedSecretFingerprint().contains(secret));
    }
}
