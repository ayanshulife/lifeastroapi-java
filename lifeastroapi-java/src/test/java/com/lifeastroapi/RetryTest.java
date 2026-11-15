package com.lifeastroapi;

import static com.lifeastroapi.Fixtures.ARJUN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifeastroapi.StubServer.Reply;
import com.lifeastroapi.errors.BadRequestException;
import com.lifeastroapi.errors.LifeAstroConnectionException;
import com.lifeastroapi.errors.LifeAstroException;
import com.lifeastroapi.errors.LifeAstroTimeoutException;
import com.lifeastroapi.errors.ServerException;
import com.lifeastroapi.params.ChartPlanetsParams;
import com.lifeastroapi.params.PanchangTithiParams;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The "retries", "abort & timeout" and network cases of the Node SDK's client.test.ts. */
class RetryTest {

    private final StubServer stub = new StubServer();
    private static final ChartPlanetsParams PLANETS = ChartPlanetsParams.builder().birth(ARJUN).build();
    private static final PanchangTithiParams TITHI = PanchangTithiParams.builder().lat(1).lon(2).tz("UTC").date("2027-01-15").build();

    @AfterEach
    void stop() {
        stub.close();
    }

    @Test
    void retriesA429ThenSucceeds() {
        AtomicInteger n = new AtomicInteger();
        stub.respond(r -> n.incrementAndGet() == 1
                ? Reply.json(429, "{\"error\":{\"code\":\"rate_limited\"}}").withHeader("Retry-After", "0")
                : Reply.ok("{\"data\":{\"ok\":true}}"));
        try (LifeAstro client = stub.client().maxRetries(2).build()) {
            assertTrue(client.chart().planets(PLANETS).path("ok").asBoolean());
        }
        assertEquals(2, stub.calls().size());
    }

    @Test
    void givesUpAfterMaxRetries() {
        stub.respond(Reply.json(503, "{\"error\":{\"code\":\"unavailable\"}}").withHeader("Retry-After", "0"));
        try (LifeAstro client = stub.client().maxRetries(2).build()) {
            ServerException e = assertThrows(ServerException.class, () -> client.chart().planets(PLANETS));
            assertEquals(503, e.status());
        }
        assertEquals(3, stub.calls().size()); // initial + 2 retries
    }

    @Test
    void clampsANegativeMaxRetriesToZero() {
        stub.respond(Reply.json(503, "{\"error\":{\"code\":\"unavailable\"}}").withHeader("Retry-After", "0"));
        try (LifeAstro client = stub.client().maxRetries(-5).build()) {
            assertEquals(0, client.maxRetries());
            assertThrows(ServerException.class, () -> client.chart().planets(PLANETS));
        }
        assertEquals(1, stub.calls().size());
    }

    @Test
    void doesNotRetryANonRetryable4xx() {
        stub.respond(Reply.json(400, "{\"error\":{\"code\":\"invalid_param\"}}"));
        try (LifeAstro client = stub.client().maxRetries(3).build()) {
            assertThrows(BadRequestException.class, () -> client.chart().planets(PLANETS));
        }
        assertEquals(1, stub.calls().size());
    }

    @Test
    void retriesEveryRetryableStatus() {
        for (int status : new int[] {408, 425, 429, 500, 502, 503, 504}) {
            stub.reset();
            AtomicInteger n = new AtomicInteger();
            stub.respond(r -> n.incrementAndGet() == 1
                    ? Reply.json(status, "{}").withHeader("Retry-After", "0")
                    : Reply.ok("{\"ok\":1}"));
            try (LifeAstro client = stub.client().maxRetries(1).build()) {
                assertEquals(1, client.meta().version().path("ok").asInt(), "status " + status);
            }
            assertEquals(2, stub.calls().size(), "status " + status);
        }
    }

    @Test
    void backoffIsExponentialWithJitterAndHonoursRetryAfterUpToThirtySeconds() {
        for (int attempt = 0; attempt < 6; attempt++) {
            long base = Math.min(500L << attempt, 8000L);
            long ms = HttpCore.backoffMs(attempt, null);
            assertTrue(ms >= base && ms < base + 250, attempt + " -> " + ms);
        }
        assertEquals(7000, HttpCore.backoffMs(0, Duration.ofSeconds(7)));
        assertEquals(30_000, HttpCore.backoffMs(0, Duration.ofMinutes(5)));
    }

    /**
     * A raw socket server: closes the first {@code drops} connections without an answer (a network
     * failure for the client), then answers 200.
     */
    private static final class FlakyServer implements AutoCloseable {
        final ServerSocket socket;
        final AtomicInteger connections = new AtomicInteger();
        final Thread thread;

        FlakyServer(int drops) throws IOException {
            socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            thread = new Thread(() -> {
                while (!socket.isClosed()) {
                    try (Socket s = socket.accept()) {
                        int n = connections.incrementAndGet();
                        if (n <= drops) {
                            continue; // closes without a response
                        }
                        InputStream in = s.getInputStream();
                        byte[] buf = new byte[8192];
                        int read = 0;
                        while (read < 4 || !new String(buf, 0, read, StandardCharsets.ISO_8859_1).contains("\r\n\r\n")) {
                            int r = in.read(buf, read, buf.length - read);
                            if (r < 0) {
                                break;
                            }
                            read += r;
                        }
                        byte[] body = "{\"data\":{\"ok\":true}}".getBytes(StandardCharsets.UTF_8);
                        OutputStream out = s.getOutputStream();
                        out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " + body.length
                                + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
                        out.write(body);
                        out.flush();
                    } catch (IOException e) {
                        // closed
                    }
                }
            }, "flaky-server");
            thread.setDaemon(true);
            thread.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + socket.getLocalPort();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    @Test
    void retriesANetworkErrorThenSucceeds() throws IOException {
        try (FlakyServer flaky = new FlakyServer(1);
                LifeAstro client = LifeAstro.builder().apiKey("dv_live_test").baseUrl(flaky.baseUrl()).maxRetries(2).build()) {
            JsonNode res = client.chart().planets(PLANETS);
            assertTrue(res.path("ok").asBoolean());
            assertEquals(2, flaky.connections.get());
        }
    }

    @Test
    void surfacesNetworkFailuresAsConnectionExceptions() throws IOException {
        int port;
        try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = s.getLocalPort();
        }
        try (LifeAstro client = LifeAstro.builder().apiKey("dv_live_test").baseUrl("http://127.0.0.1:" + port).maxRetries(0).build()) {
            LifeAstroConnectionException e = assertThrows(LifeAstroConnectionException.class, () -> client.panchang().tithi(TITHI));
            assertEquals("connection_error", e.code());
            assertEquals(0, e.status());
            assertTrue(e.getMessage().startsWith("Network request failed"), e.getMessage());
        }
    }

    @Test
    void timesOutWithCodeTimeout() {
        stub.respond(Reply.ok("{}").delayed(2000));
        try (LifeAstro client = stub.client().build()) {
            long start = System.nanoTime();
            LifeAstroTimeoutException e = assertThrows(LifeAstroTimeoutException.class,
                    () -> client.panchang().tithi(TITHI, RequestOptions.timeout(Duration.ofMillis(200))));
            assertEquals("timeout", e.code());
            assertEquals("Request timed out after 200ms", e.getMessage());
            assertTrue(System.nanoTime() - start < 1_500_000_000L, "returned before the server answered");
        }
    }

    @Test
    void timeoutsAreRetriedLikeNetworkErrors() {
        AtomicInteger n = new AtomicInteger();
        stub.respond(r -> n.incrementAndGet() == 1 ? Reply.ok("{}").delayed(1500) : Reply.ok("{\"ok\":2}"));
        try (LifeAstro client = stub.client().maxRetries(1).timeout(Duration.ofMillis(300)).build()) {
            assertEquals(2, client.meta().version().path("ok").asInt());
        }
    }

    @Test
    void anInterruptedCallerGetsCodeAborted() throws InterruptedException {
        stub.respond(Reply.ok("{}").delayed(3000));
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicReference<Boolean> stillInterrupted = new AtomicReference<>();
        try (LifeAstro client = stub.client().build()) {
            Thread t = new Thread(() -> {
                try {
                    client.panchang().tithi(TITHI);
                } catch (RuntimeException e) {
                    thrown.set(e);
                    stillInterrupted.set(Thread.currentThread().isInterrupted());
                }
            });
            t.start();
            Thread.sleep(300);
            t.interrupt();
            t.join(5000);
        }
        LifeAstroException e = assertInstanceOf(LifeAstroConnectionException.class, thrown.get());
        assertEquals("aborted", e.code());
        assertTrue(stillInterrupted.get(), "the interrupt flag is restored");
    }
}
