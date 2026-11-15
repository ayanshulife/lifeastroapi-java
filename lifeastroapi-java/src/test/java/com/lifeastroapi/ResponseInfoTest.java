package com.lifeastroapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lifeastroapi.StubServer.Reply;
import com.lifeastroapi.errors.LifeAstroException;
import com.lifeastroapi.params.PanchangBasicParams;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The four "onResponse (credits, rate limit, request id)" cases of the Node SDK's client.test.ts. */
class ResponseInfoTest {

    private static final Map<String, String> HEADERS = Map.of(
            "X-Request-ID", "52a520f261d3db37",
            "X-Credits-Remaining", "64043",
            "X-RateLimit-Limit", "250",
            "X-RateLimit-Remaining", "249",
            "X-RateLimit-Reset", "1794718800",
            "X-Cache", "HIT",
            "Content-Language", "gu");

    private final StubServer stub = new StubServer();

    @AfterEach
    void stop() {
        stub.close();
    }

    @Test
    void reportsEveryHeaderOnASuccessClientLevelAndPerCall() {
        stub.respond(Reply.ok("{\"ok\":1}").withHeaders(HEADERS));
        List<ResponseInfo> seen = new CopyOnWriteArrayList<>();
        List<ResponseInfo> perCall = new CopyOnWriteArrayList<>();
        try (LifeAstro client = stub.client().onResponse(seen::add).build()) {
            client.panchang().basic(PanchangBasicParams.builder().lat(1).lon(2).tz("UTC").lang(Lang.GU).build(),
                    RequestOptions.onResponse(perCall::add));
        }
        assertEquals(1, seen.size());
        assertEquals(seen, perCall);
        assertEquals(new ResponseInfo(200, "/v1/panchang/basic", "52a520f261d3db37", 64043L,
                new RateLimit(250, 249, Instant.ofEpochSecond(1794718800)), "HIT", "gu"), seen.get(0));
    }

    @Test
    void leavesOutHeadersTheApiDidNotSend() {
        stub.respond(Reply.ok("{\"ok\":1}").withHeader("X-Request-ID", "abc"));
        List<ResponseInfo> seen = new CopyOnWriteArrayList<>();
        try (LifeAstro client = stub.client().onResponse(seen::add).build()) {
            client.meta().version();
        }
        assertEquals(List.of(new ResponseInfo(200, "/v1/meta/version", "abc", null, null, null, null)), seen);
    }

    @Test
    void reportsTheFinalErrorResponseOnceNotTheRetriedAttempts() {
        AtomicInteger n = new AtomicInteger();
        stub.respond(r -> Reply.json(503, "{\"error\":{\"code\":\"billing_unavailable\",\"message\":\"x\"}}")
                .withHeaders(Map.of("X-Request-ID", "r" + n.incrementAndGet(), "Retry-After", "0")));
        List<ResponseInfo> seen = new CopyOnWriteArrayList<>();
        try (LifeAstro client = stub.client().maxRetries(2).onResponse(seen::add).build()) {
            assertThrows(LifeAstroException.class, () -> client.meta().version());
        }
        assertEquals(3, n.get());
        assertEquals(1, seen.size());
        assertEquals(503, seen.get(0).status());
        assertEquals("r3", seen.get(0).requestId());
    }

    @Test
    void aThrowingHookNeverBreaksTheCall() {
        stub.respond(Reply.ok("{\"v\":7}").withHeaders(HEADERS));
        try (LifeAstro client = stub.client().onResponse(i -> {
            throw new IllegalStateException("logger down");
        }).build()) {
            assertEquals(7, client.meta().version(RequestOptions.onResponse(i -> {
                throw new IllegalStateException("also down");
            })).path("v").asInt());
        }
    }
}
