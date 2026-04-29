package net.rustcore.duel.rating;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.*;

class RatingClientTest {

    private WireMockServer wm;

    @BeforeEach
    void start() { wm = new WireMockServer(options().dynamicPort()); wm.start(); }

    @AfterEach
    void stop() { wm.stop(); }

    @Test
    void postsSignedRequestAndDeserializesResponse() throws Exception {
        wm.stubFor(post(urlEqualTo("/v1/rate"))
                .willReturn(okJson("{\"mode_id\":\"k\",\"model\":\"PlackettLuce\",\"players\":[" +
                        "{\"uuid\":\"" + "a".repeat(36) + "\",\"mu_before\":25.0,\"sigma_before\":8.33,\"mu_after\":26.0,\"sigma_after\":7.9,\"ordinal_after\":2.3,\"delta_ordinal\":3.0}]}")));

        RatingConfig cfg = new RatingConfig(true, wm.baseUrl(), "x".repeat(32), 2000, 5000);
        RatingClient client = new RatingClient(cfg);

        var body = new RatingRequest.Body("k", List.of(
                new RatingRequest.Team(0, List.of(new RatingRequest.PlayerRating("a".repeat(36), 25.0, 8.33))),
                new RatingRequest.Team(1, List.of(new RatingRequest.PlayerRating("b".repeat(36), 25.0, 8.33)))
        ));

        CompletableFuture<RatingResponse.Body> fut = client.rate(body);
        RatingResponse.Body resp = fut.get();

        assertEquals("k", resp.mode_id());
        assertEquals(1, resp.players().size());

        wm.verify(postRequestedFor(urlEqualTo("/v1/rate"))
                .withHeader("X-Rating-Timestamp", matching("\\d+"))
                .withHeader("X-Rating-Signature", matching("[0-9a-f]{64}")));
    }

    @Test
    void rateFailsFastWhenDisabled() {
        RatingConfig cfg = new RatingConfig(false, "http://nowhere", "", 1000, 1000);
        RatingClient client = new RatingClient(cfg);
        var body = new RatingRequest.Body("k", List.of());
        var ex = assertThrows(java.util.concurrent.ExecutionException.class, () -> client.rate(body).get());
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    void rateThrowsOnInvalidJson() throws Exception {
        wm.stubFor(post(urlEqualTo("/v1/rate"))
                .willReturn(ok().withHeader("Content-Type", "application/json").withBody("not-json")));

        RatingConfig cfg = new RatingConfig(true, wm.baseUrl(), "x".repeat(32), 2000, 5000);
        RatingClient client = new RatingClient(cfg);

        var body = new RatingRequest.Body("k", List.of(
                new RatingRequest.Team(0, List.of(new RatingRequest.PlayerRating("a".repeat(36), 25.0, 8.33))),
                new RatingRequest.Team(1, List.of(new RatingRequest.PlayerRating("b".repeat(36), 25.0, 8.33)))
        ));

        var ex = assertThrows(java.util.concurrent.ExecutionException.class, () -> client.rate(body).get());
        assertTrue(ex.getCause().getMessage().contains("rating parse failed"));
    }
}
