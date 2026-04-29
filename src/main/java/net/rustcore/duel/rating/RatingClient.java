package net.rustcore.duel.rating;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public final class RatingClient {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE);

    private final RatingConfig cfg;
    private final HttpClient http;
    private final Logger logger;

    public RatingClient(RatingConfig cfg) {
        this(cfg, null);
    }

    public RatingClient(RatingConfig cfg, Logger logger) {
        this.cfg = cfg;
        this.logger = logger;
        // connectTimeout = TCP handshake; per-request timeout set on HttpRequest covers full round-trip.
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(cfg.connectTimeoutMs()))
                .build();
    }

    /**
     * Returns a future that fails with {@link java.util.concurrent.CompletionException} on HTTP or parse error.
     * Call {@code getCause()} to unwrap the underlying {@link RuntimeException}.
     */
    public CompletableFuture<RatingResponse.Body> rate(RatingRequest.Body body) {
        if (!cfg.enabled()) {
            return CompletableFuture.failedFuture(new IllegalStateException("rating disabled"));
        }
        return post("/v1/rate", body, RatingResponse.Body.class);
    }

    private <T> CompletableFuture<T> post(String path, Object body, Class<T> respType) {
        byte[] payload;
        try {
            payload = MAPPER.writeValueAsBytes(body);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }

        String ts = Long.toString(System.currentTimeMillis() / 1000L);
        String sig;
        try {
            sig = sign(cfg.sharedSecret(), ts, payload);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
        if (logger != null) {
            logger.info("rating request auth path=" + path
                    + " timestamp=" + ts
                    + " secret_len=" + (cfg.sharedSecret() == null ? 0 : cfg.sharedSecret().length())
                    + " secret_fp=" + cfg.sharedSecretFingerprint()
                    + " body_sha256=" + sha256Prefix(payload)
                    + " signature_prefix=" + sig.substring(0, 12));
        }

        HttpRequest req = HttpRequest.newBuilder(URI.create(cfg.baseUrl() + path))
                .timeout(Duration.ofMillis(cfg.requestTimeoutMs()))
                .header("Content-Type", "application/json")
                .header("X-Rating-Timestamp", ts)
                .header("X-Rating-Signature", sig)
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();

        return http.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(resp -> {
                    if (resp.statusCode() / 100 != 2) {
                        throw new RuntimeException("rating service " + resp.statusCode()
                                + ": " + new String(resp.body(), StandardCharsets.UTF_8));
                    }
                    try {
                        return MAPPER.readValue(resp.body(), respType);
                    } catch (Exception e) {
                        throw new RuntimeException("rating parse failed", e);
                    }
                });
    }

    static String sign(String secret, String ts, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update(ts.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '.');
        mac.update(body);
        return HexFormat.of().formatHex(mac.doFinal());
    }

    private static String sha256Prefix(byte[] body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body);
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (Exception e) {
            return "<unavailable>";
        }
    }
}
