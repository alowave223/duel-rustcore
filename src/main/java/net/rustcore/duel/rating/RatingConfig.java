package net.rustcore.duel.rating;

import org.bukkit.configuration.ConfigurationSection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public record RatingConfig(
        boolean enabled,
        String baseUrl,
        String sharedSecret,
        int connectTimeoutMs,
        int requestTimeoutMs
) {
    public static RatingConfig fromSection(ConfigurationSection s) {
        // Loopback fallback only — production default lives in config.yml (Tailnet IP).
        if (s == null) return new RatingConfig(false, "", "", 2000, 5000);
        return new RatingConfig(
                s.getBoolean("enabled", false),
                s.getString("base-url", "http://127.0.0.1:8088"),
                s.getString("shared-secret", ""),
                s.getInt("connect-timeout-ms", 2000),
                s.getInt("request-timeout-ms", 5000)
        );
    }

    public String sharedSecretFingerprint() {
        if (sharedSecret == null || sharedSecret.isEmpty()) return "<empty>";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(sharedSecret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (Exception e) {
            return "<unavailable>";
        }
    }

    @Override
    public String toString() {
        return "RatingConfig[enabled=" + enabled
                + ", baseUrl=" + baseUrl
                + ", sharedSecret=" + (sharedSecret == null || sharedSecret.isEmpty()
                        ? "<empty>"
                        : "<redacted:" + sharedSecret.length() + ",fp=" + sharedSecretFingerprint() + ">")
                + ", connectTimeoutMs=" + connectTimeoutMs
                + ", requestTimeoutMs=" + requestTimeoutMs + "]";
    }
}
