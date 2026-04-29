package net.rustcore.duel.rating;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;

public final class RatingClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RatingConfig cfg;
    private final HttpClient http;

    public RatingClient(RatingConfig cfg) {
        this.cfg = cfg;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(cfg.connectTimeoutMs()))
                .build();
    }

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
}
