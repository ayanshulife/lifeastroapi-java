package com.lifeastroapi;

import static com.lifeastroapi.Fixtures.ARJUN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifeastroapi.StubServer.Reply;
import com.lifeastroapi.errors.AuthenticationException;
import com.lifeastroapi.errors.BadRequestException;
import com.lifeastroapi.errors.LifeAstroException;
import com.lifeastroapi.errors.NotFoundException;
import com.lifeastroapi.errors.PaymentRequiredException;
import com.lifeastroapi.errors.PermissionException;
import com.lifeastroapi.errors.RateLimitException;
import com.lifeastroapi.errors.ServerException;
import com.lifeastroapi.params.ChartPlanetsParams;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The "errors" and "error status mapping" cases of the Node SDK's client.test.ts. */
class ErrorsTest {

    private final StubServer stub = new StubServer();

    @AfterEach
    void stop() {
        stub.close();
    }

    private LifeAstroException fail(int status, String body, Map<String, String> headers) {
        stub.respond(Reply.json(status, body).withHeaders(headers));
        try (LifeAstro client = stub.client().build()) {
            return assertThrows(LifeAstroException.class,
                    () -> client.chart().planets(ChartPlanetsParams.builder().birth(ARJUN).build()));
        }
    }

    @Test
    void maps401ToAuthenticationExceptionWithCodeAndRequestId() {
        LifeAstroException e = fail(401, "{\"error\":{\"code\":\"unauthorized\",\"message\":\"bad key\",\"request_id\":\"req_1\"}}", Map.of());
        assertInstanceOf(AuthenticationException.class, e);
        assertEquals("unauthorized", e.code());
        assertEquals(401, e.status());
        assertEquals("req_1", e.requestId());
        assertEquals("bad key", e.getMessage());
        assertNotNull(e.body());
        assertTrue(e.toString().contains("401 unauthorized, request req_1"), e.toString());
    }

    @Test
    void maps402ToPaymentRequired() {
        assertInstanceOf(PaymentRequiredException.class,
                fail(402, "{\"error\":{\"code\":\"insufficient_credits\",\"message\":\"no credits\"}}", Map.of()));
    }

    @Test
    void maps400ToBadRequest() {
        LifeAstroException e = fail(400, "{\"error\":{\"code\":\"invalid_param\",\"message\":\"bad lat\"}}", Map.of());
        assertInstanceOf(BadRequestException.class, e);
        assertEquals("invalid_param", e.code());
    }

    @Test
    void maps429ToRateLimitWithRetryAfter() {
        LifeAstroException e = fail(429, "{\"error\":{\"code\":\"rate_limited\",\"message\":\"slow down\"}}", Map.of("Retry-After", "7"));
        RateLimitException r = assertInstanceOf(RateLimitException.class, e);
        assertEquals(Duration.ofSeconds(7), r.retryAfter());
    }

    @Test
    void parsesTheHttpDateFormOfRetryAfter() {
        LifeAstroException e = fail(429, "{\"error\":{\"code\":\"rate_limited\"}}", Map.of("Retry-After", "Wed, 21 Oct 2099 07:28:00 GMT"));
        Duration ra = assertInstanceOf(RateLimitException.class, e).retryAfter();
        assertNotNull(ra);
        assertTrue(ra.toSeconds() > 0);
        assertEquals(Duration.ofSeconds(60),
                HttpCore.parseRetryAfter("Wed, 21 Oct 2026 07:28:00 GMT", Instant.parse("2026-10-21T07:27:00Z")));
        assertEquals(Duration.ZERO, HttpCore.parseRetryAfter("-3", Instant.now()));
        assertEquals(Duration.ofMillis(1500), HttpCore.parseRetryAfter(" 1.5 ", Instant.now()));
        assertEquals(null, HttpCore.parseRetryAfter("soon", Instant.now()));
    }

    @Test
    void statusMapping() {
        record Case(int status, Class<? extends LifeAstroException> type) {
        }
        for (Case k : new Case[] {new Case(403, PermissionException.class), new Case(404, NotFoundException.class),
                new Case(500, ServerException.class), new Case(503, ServerException.class),
                new Case(418, LifeAstroException.class)}) {
            LifeAstroException e = fail(k.status(), "{\"error\":{\"code\":\"x\",\"message\":\"m\"}}", Map.of());
            assertEquals(k.type(), e.getClass(), "status " + k.status());
            assertEquals(k.status(), e.status());
        }
    }

    @Test
    void defaultCodesWhenTheBodyHasNoError() {
        LifeAstroException e = fail(502, "<html>bad gateway</html>", Map.of("X-Request-ID", "hdr_1"));
        assertEquals("internal_error", e.code());
        assertEquals("HTTP 502", e.getMessage());
        assertEquals("hdr_1", e.requestId());
        assertEquals("bad gateway", e.body().asText().replaceAll("</?html>", ""));
        assertEquals("bad_request", fail(409, "{}", Map.of()).code());
    }
}
