package com.lifeastroapi.spring;

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
import java.util.function.Function;

/** A local stand-in for the API: records requests and answers JSON. */
public final class TestApi implements AutoCloseable {

    /** A recorded request. */
    public record Request(URI uri, String authorization) {
        public String path() {
            return uri.getRawPath();
        }

        public Map<String, String> query() {
            Map<String, String> out = new LinkedHashMap<>();
            String raw = uri.getRawQuery();
            if (raw != null) {
                for (String pair : raw.split("&")) {
                    int eq = pair.indexOf('=');
                    out.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                            URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
                }
            }
            return out;
        }
    }

    private final HttpServer server;
    private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());
    private volatile Function<Request, String> body = r -> "{\"path\":\"" + r.path() + "\"}";

    public TestApi() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", exchange -> {
            Request r = new Request(exchange.getRequestURI(), exchange.getRequestHeaders().getFirst("Authorization"));
            requests.add(r);
            byte[] bytes = body.apply(r).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("X-Credits-Remaining", "64043");
            exchange.getResponseHeaders().set("X-Request-ID", "req_" + requests.size());
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public TestApi answer(Function<Request, String> body) {
        this.body = body;
        return this;
    }

    public List<Request> requests() {
        synchronized (requests) {
            return List.copyOf(requests);
        }
    }

    public List<Request> requests(String path) {
        return requests().stream().filter(r -> r.path().equals(path)).toList();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
