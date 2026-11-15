package com.lifeastroapi;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * The asynchronous view of a {@link LifeAstro} client, from {@link LifeAstro#async()}: the same
 * resource groups and methods, each returning a {@link CompletableFuture}. Behaviour is the same as
 * the blocking methods (retries, timeout, {@code onResponse}); retry waits are scheduled, not
 * slept. A failed call completes the future exceptionally with a
 * {@link com.lifeastroapi.errors.LifeAstroException}; cancelling the future aborts the request.
 *
 * <pre>{@code
 * CompletableFuture<JsonNode> day = client.async().panchang().basic(params);
 * }</pre>
 */
public final class LifeAstroAsync extends LifeAstroAsyncResources {

    private final LifeAstro client;

    LifeAstroAsync(LifeAstro client) {
        this.client = client;
    }

    /**
     * The blocking client this view belongs to.
     *
     * @return the client
     */
    public LifeAstro client() {
        return client;
    }

    /**
     * Calls any {@code /v1/*} endpoint directly.
     *
     * @param path the path, e.g. {@code /v1/panchang/tithi}
     * @return the response JSON
     */
    public CompletableFuture<JsonNode> request(String path) {
        return client.core.sendAsync(path, null, JsonNode.class, null);
    }

    /**
     * Calls any {@code /v1/*} endpoint directly.
     *
     * @param path  the path
     * @param query query parameters by wire name; null and empty values are left out
     * @return the response JSON
     */
    public CompletableFuture<JsonNode> request(String path, Map<String, ?> query) {
        return client.core.sendAsync(path, query, JsonNode.class, null);
    }

    /**
     * Calls any {@code /v1/*} endpoint directly, with per-call options.
     *
     * @param path    the path
     * @param query   query parameters by wire name, or null
     * @param options per-call options, or null
     * @return the response JSON
     */
    public CompletableFuture<JsonNode> request(String path, Map<String, ?> query, RequestOptions options) {
        return client.core.sendAsync(path, query, JsonNode.class, options);
    }

    /**
     * Calls any {@code /v1/*} endpoint directly and maps the response to {@code type}.
     *
     * @param <T>   the result type
     * @param path  the path
     * @param query query parameters by wire name, or null
     * @param type  the class to map the response to
     * @return the mapped response
     */
    public <T> CompletableFuture<T> request(String path, Map<String, ?> query, Class<T> type) {
        return client.core.sendAsync(path, query, Objects.requireNonNull(type, "type"), null);
    }

    /**
     * Calls any {@code /v1/*} endpoint directly, maps the response to {@code type}, with per-call
     * options. Every generated asynchronous method ends here.
     *
     * @param <T>     the result type
     * @param path    the path
     * @param query   query parameters by wire name, or null
     * @param type    the class to map the response to
     * @param options per-call options, or null
     * @return the mapped response
     */
    public <T> CompletableFuture<T> request(String path, Map<String, ?> query, Class<T> type,
            RequestOptions options) {
        return client.core.sendAsync(path, query, Objects.requireNonNull(type, "type"), options);
    }

    @Override
    public String toString() {
        return "LifeAstroAsync{" + client + "}";
    }
}
