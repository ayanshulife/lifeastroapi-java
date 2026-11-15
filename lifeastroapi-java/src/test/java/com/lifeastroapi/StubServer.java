package com.lifeastroapi;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * A local HTTP server for the tests ({@code com.sun.net.httpserver}): records every request and
 * answers with what the responder returns. Bound to 127.0.0.1 on a free port.
 */
final class StubServer implements AutoCloseable {

    /** A recorded request. */
    record Recorded(String method, URI uri, Headers headers) {
        String path() {
            return uri.getRawPath();
        }

        /** The raw path and query, e.g. {@code /v1/panchang/basic?lat=19.076&...}. */
        String pathAndQuery() {
            return uri.getRawQuery() == null ? uri.getRawPath() : uri.getRawPath() + "?" + uri.getRawQuery();
        }

        /** Decoded query parameters, in the order sent. */
        Map<String, String> query() {
            Map<String, String> out = new LinkedHashMap<>();
            String raw = uri.getRawQuery();
            if (raw == null || raw.isEmpty()) {
                return out;
            }
            for (String pair : raw.split("&")) {
                int eq = pair.indexOf('=');
                String k = eq < 0 ? pair : pair.substring(0, eq);
                String v = eq < 0 ? "" : pair.substring(eq + 1);
                out.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
            }
            return out;
        }

        String header(String name) {
            return headers.getFirst(name);
        }
    }

    /** A canned response. */
    record Reply(int status, byte[] body, Map<String, String> headers, long delayMs) {
        static Reply json(int status, String body) {
            return new Reply(status, body.getBytes(StandardCharsets.UTF_8), Map.of("Content-Type", "application/json"), 0);
        }

        static Reply ok(String body) {
            return json(200, body);
        }

        Reply withHeaders(Map<String, String> extra) {
            Map<String, String> h = new LinkedHashMap<>(headers);
            h.putAll(extra);
            return new Reply(status, body, h, delayMs);
        }

        Reply withHeader(String name, String value) {
            return withHeaders(Map.of(name, value));
        }

        Reply delayed(long ms) {
            return new Reply(status, body, headers, ms);
        }
    }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "stub-server");
        t.setDaemon(true);
        return t;
    });
    private final List<Recorded> calls = Collections.synchronizedList(new ArrayList<>());
    private volatile Function<Recorded, Reply> responder = r -> Reply.ok("{}");

    StubServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    StubServer respond(Function<Recorded, Reply> responder) {
        this.responder = responder;
        return this;
    }

    StubServer respond(Reply reply) {
        return respond(r -> reply);
    }

    List<Recorded> calls() {
        synchronized (calls) {
            return List.copyOf(calls);
        }
    }

    Recorded last() {
        List<Recorded> all = calls();
        if (all.isEmpty()) {
            throw new AssertionError("no request reached the stub server");
        }
        return all.get(all.size() - 1);
    }

    void reset() {
        calls.clear();
    }

    /** A client for this server: key {@code dv_live_test}, no retries unless the test sets them. */
    LifeAstro.Builder client() {
        return LifeAstro.builder().apiKey("dv_live_test").baseUrl(baseUrl()).maxRetries(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        Recorded rec = new Recorded(exchange.getRequestMethod(), exchange.getRequestURI(), exchange.getRequestHeaders());
        calls.add(rec);
        Reply reply;
        try {
            reply = responder.apply(rec);
        } catch (RuntimeException e) {
            reply = Reply.json(599, "{\"error\":{\"code\":\"stub_failure\",\"message\":\"" + e + "\"}}");
        }
        if (reply.delayMs() > 0) {
            try {
                Thread.sleep(reply.delayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        reply.headers().forEach((k, v) -> exchange.getResponseHeaders().set(k, v));
        byte[] body = reply.body();
        try {
            exchange.sendResponseHeaders(reply.status(), body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
        } catch (IOException e) {
            // The client went away (timeout tests).
        } finally {
            exchange.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
