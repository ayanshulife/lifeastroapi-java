package com.lifeastroapi;

import static com.lifeastroapi.Fixtures.ARJUN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifeastroapi.StubServer.Reply;
import com.lifeastroapi.errors.AuthenticationException;
import com.lifeastroapi.errors.LifeAstroTimeoutException;
import com.lifeastroapi.params.ChartPlanetsParams;
import com.lifeastroapi.params.MilanAshtakootaParams;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** {@link LifeAstro#async()}: the same behaviour as the blocking methods, as CompletableFuture. */
class AsyncTest {

    private final StubServer stub = new StubServer();

    @AfterEach
    void stop() {
        stub.close();
    }

    @Test
    void completesWithTheUnwrappedBody() throws Exception {
        stub.respond(Reply.ok("{\"data\":{\"ok\":true}}"));
        try (LifeAstro client = stub.client().build()) {
            CompletableFuture<JsonNode> f = client.async().chart().planets(ChartPlanetsParams.builder().birth(ARJUN).build());
            assertTrue(f.get(5, TimeUnit.SECONDS).path("ok").asBoolean());
        }
        assertEquals("1990-01-15", stub.last().query().get("date"));
    }

    @Test
    void completesExceptionallyWithTheTypedException() {
        stub.respond(Reply.json(401, "{\"error\":{\"code\":\"unauthorized\",\"message\":\"bad key\",\"request_id\":\"req_9\"}}"));
        try (LifeAstro client = stub.client().build()) {
            ExecutionException e = assertThrows(ExecutionException.class, () -> client.async().meta().version().get(5, TimeUnit.SECONDS));
            AuthenticationException auth = assertInstanceOf(AuthenticationException.class, e.getCause());
            assertEquals("req_9", auth.requestId());
        }
    }

    @Test
    void retriesWithoutBlockingAndReportsOnce() throws Exception {
        AtomicInteger n = new AtomicInteger();
        stub.respond(r -> n.incrementAndGet() < 3
                ? Reply.json(503, "{}").withHeader("Retry-After", "0")
                : Reply.ok("{\"ok\":3}").withHeader("X-Credits-Remaining", "99"));
        List<ResponseInfo> seen = new CopyOnWriteArrayList<>();
        try (LifeAstro client = stub.client().maxRetries(2).onResponse(seen::add).build()) {
            assertEquals(3, client.async().meta().version().get(5, TimeUnit.SECONDS).path("ok").asInt());
        }
        assertEquals(3, n.get());
        assertEquals(1, seen.size());
        assertEquals(99L, seen.get(0).creditsRemaining());
    }

    @Test
    void mapsToYourClass() throws Exception {
        stub.respond(Reply.ok("{\"total\":28.5,\"max\":36}"));
        try (LifeAstro client = stub.client().build()) {
            Map<?, ?> m = client.async().milan().ashtakoota(MilanAshtakootaParams.builder().boy(ARJUN).girl(Fixtures.GIRL).build(),
                    Map.class).get(5, TimeUnit.SECONDS);
            assertEquals(28.5, m.get("total"));
        }
    }

    @Test
    void timesOut() {
        stub.respond(Reply.ok("{}").delayed(2000));
        try (LifeAstro client = stub.client().build()) {
            ExecutionException e = assertThrows(ExecutionException.class, () -> client.async().meta()
                    .version(RequestOptions.timeout(Duration.ofMillis(200))).get(5, TimeUnit.SECONDS));
            assertEquals("timeout", assertInstanceOf(LifeAstroTimeoutException.class, e.getCause()).code());
        }
    }

    @Test
    void cancellingTheFutureStopsWaiting() {
        stub.respond(Reply.ok("{}").delayed(2000));
        try (LifeAstro client = stub.client().build()) {
            CompletableFuture<JsonNode> f = client.async().meta().version();
            assertTrue(f.cancel(true));
            assertTrue(f.isCancelled());
        }
    }

    @Test
    void escapeHatch() throws Exception {
        stub.respond(Reply.ok("{\"data\":{\"v\":1}}"));
        try (LifeAstro client = stub.client().build()) {
            assertEquals(1, client.async().request("/v1/anything", Map.of("a", "b")).get(5, TimeUnit.SECONDS).path("v").asInt());
        }
        assertEquals("b", stub.last().query().get("a"));
    }

    @Test
    void sharedClientUnderConcurrentCalls() throws Exception {
        stub.respond(r -> Reply.ok("{\"echo\":\"" + r.query().get("date") + "\"}"));
        try (LifeAstro client = stub.client().build()) {
            List<CompletableFuture<String>> all = new java.util.ArrayList<>();
            for (int i = 1; i <= 40; i++) {
                String date = String.format("2027-01-%02d", (i % 28) + 1);
                all.add(client.async().chart().planets(ChartPlanetsParams.builder()
                        .birth(BirthInput.of(date, "10:30", "Asia/Kolkata", 19.076, 72.8777)).build())
                        .thenApply(n -> n.path("echo").asText() + "=" + date));
            }
            for (CompletableFuture<String> f : all) {
                String[] parts = f.get(10, TimeUnit.SECONDS).split("=");
                assertEquals(parts[1], parts[0]);
            }
        }
    }
}
