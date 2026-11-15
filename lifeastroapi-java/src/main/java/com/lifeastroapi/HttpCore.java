package com.lifeastroapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.lifeastroapi.errors.LifeAstroConnectionException;
import com.lifeastroapi.errors.LifeAstroException;
import com.lifeastroapi.errors.LifeAstroTimeoutException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * The request pipeline behind every method, a port of {@code src/http.ts} of the Node.js SDK:
 * URL and query building, bearer auth, timeout, JSON decoding, {@code { data, meta }} envelope
 * unwrapping, error mapping, bounded retries with exponential backoff that honour
 * {@code Retry-After}, and the {@code onResponse} hook. The blocking and the
 * {@link CompletableFuture} paths share {@link #evaluate}, so they behave the same.
 */
final class HttpCore {

    /** Statuses retried automatically (with backoff, up to {@code maxRetries} times). */
    static final Set<Integer> RETRYABLE_STATUS = Set.of(408, 425, 429, 500, 502, 503, 504);

    /** Extra time on top of the request timeout before the whole call is abandoned. */
    private static final long GRACE_MS = 250;

    private final String apiKey;
    private final String baseUrl;
    private final Duration timeout;
    private final int maxRetries;
    private final HttpClient http;
    private final boolean ownsHttp;
    private final String userAgent;
    private final Map<String, String> defaultHeaders;
    private final Consumer<ResponseInfo> onResponse;
    private final ObjectMapper mapper;
    private final ObjectReader reader;
    private volatile boolean closed;

    HttpCore(String apiKey, String baseUrl, Duration timeout, int maxRetries, HttpClient http, boolean ownsHttp,
            String userAgent, Map<String, String> defaultHeaders, Consumer<ResponseInfo> onResponse,
            ObjectMapper mapper) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.timeout = timeout;
        this.maxRetries = maxRetries;
        this.http = http;
        this.ownsHttp = ownsHttp;
        this.userAgent = userAgent;
        this.defaultHeaders = Map.copyOf(defaultHeaders);
        this.onResponse = onResponse;
        this.mapper = mapper;
        this.reader = mapper.readerFor(JsonNode.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    String baseUrl() {
        return baseUrl;
    }

    Duration timeout() {
        return timeout;
    }

    int maxRetries() {
        return maxRetries;
    }

    String maskedKey() {
        int cut = apiKey.lastIndexOf('_');
        return (cut < 0 ? "" : apiKey.substring(0, cut + 1)) + "****";
    }

    // ------------------------------------------------------------------------------------------
    // Blocking path
    // ------------------------------------------------------------------------------------------

    <T> T send(String path, Map<String, ?> query, Class<T> type, RequestOptions options) {
        Call call = newCall(path, query, options);
        for (int attempt = 0;; attempt++) {
            HttpResponse<byte[]> response = null;
            LifeAstroException failure = null;
            CompletableFuture<HttpResponse<byte[]>> future =
                    http.sendAsync(call.request, HttpResponse.BodyHandlers.ofByteArray());
            try {
                response = future.get(call.timeoutMs + GRACE_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                throw new LifeAstroConnectionException("Request interrupted", "aborted", e);
            } catch (TimeoutException e) {
                future.cancel(true);
                failure = timedOut(call, e);
            } catch (ExecutionException e) {
                failure = networkFailure(e.getCause(), call);
            }
            Step step = evaluate(call, attempt, response, failure);
            if (step.error != null) {
                throw step.error;
            }
            if (step.done) {
                return convert(call, step, type);
            }
            try {
                Thread.sleep(step.delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LifeAstroConnectionException("Request interrupted", "aborted", e);
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // CompletableFuture path
    // ------------------------------------------------------------------------------------------

    <T> CompletableFuture<T> sendAsync(String path, Map<String, ?> query, Class<T> type, RequestOptions options) {
        // A closed client or a malformed path is a programming error: thrown here, as when blocking.
        Call call = newCall(path, query, options);
        CompletableFuture<T> result = new CompletableFuture<>();
        result.whenComplete((value, error) -> {
            if (result.isCancelled()) {
                CompletableFuture<?> inFlight = call.inFlight;
                if (inFlight != null) {
                    inFlight.cancel(true);
                }
            }
        });
        attemptAsync(call, 0, result, type);
        return result;
    }

    private <T> void attemptAsync(Call call, int attempt, CompletableFuture<T> result, Class<T> type) {
        if (result.isDone()) {
            return;
        }
        CompletableFuture<HttpResponse<byte[]>> future;
        try {
            ensureOpen();
            future = http.sendAsync(call.request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (RuntimeException e) {
            result.completeExceptionally(e);
            return;
        }
        call.inFlight = future;
        future.orTimeout(call.timeoutMs + GRACE_MS, TimeUnit.MILLISECONDS).whenComplete((response, error) -> {
            try {
                LifeAstroException failure = null;
                if (error != null) {
                    Throwable cause = unwrap(error);
                    if (cause instanceof CancellationException) {
                        result.completeExceptionally(
                                new LifeAstroConnectionException("Request cancelled", "aborted", cause));
                        return;
                    }
                    failure = cause instanceof TimeoutException
                            ? timedOut(call, cause)
                            : networkFailure(cause, call);
                }
                Step step = evaluate(call, attempt, error == null ? response : null, failure);
                if (step.error != null) {
                    result.completeExceptionally(step.error);
                } else if (step.done) {
                    result.complete(convert(call, step, type));
                } else {
                    CompletableFuture.delayedExecutor(step.delayMs, TimeUnit.MILLISECONDS)
                            .execute(() -> attemptAsync(call, attempt + 1, result, type));
                }
            } catch (RuntimeException | Error e) {
                result.completeExceptionally(e);
            }
        });
    }

    // ------------------------------------------------------------------------------------------
    // Shared logic
    // ------------------------------------------------------------------------------------------

    /** One logical call: the request (the same for every attempt) and its options. */
    private static final class Call {
        final String path;
        final HttpRequest request;
        final long timeoutMs;
        final RequestOptions options;
        volatile CompletableFuture<?> inFlight;

        Call(String path, HttpRequest request, long timeoutMs, RequestOptions options) {
            this.path = path;
            this.request = request;
            this.timeoutMs = timeoutMs;
            this.options = options;
        }
    }

    /** What to do after an attempt: return a value, throw an error, or retry after a delay. */
    private static final class Step {
        final boolean done;
        final JsonNode value;
        final JsonNode body;
        final int status;
        final String requestId;
        final LifeAstroException error;
        final long delayMs;

        private Step(boolean done, JsonNode value, JsonNode body, int status, String requestId,
                LifeAstroException error, long delayMs) {
            this.done = done;
            this.value = value;
            this.body = body;
            this.status = status;
            this.requestId = requestId;
            this.error = error;
            this.delayMs = delayMs;
        }

        static Step done(JsonNode value, JsonNode body, int status, String requestId) {
            return new Step(true, value, body, status, requestId, null, 0);
        }

        static Step fail(LifeAstroException error) {
            return new Step(false, null, null, 0, null, error, 0);
        }

        static Step retry(long delayMs) {
            return new Step(false, null, null, 0, null, null, delayMs);
        }
    }

    private Call newCall(String path, Map<String, ?> query, RequestOptions options) {
        ensureOpen();
        Objects.requireNonNull(path, "path");
        String p = path.startsWith("/") ? path : "/" + path;
        RequestOptions opts = options == null ? RequestOptions.none() : options;
        Duration t = opts.getTimeout() != null ? opts.getTimeout() : timeout;
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + p + QueryString.encode(query)))
                .GET()
                .timeout(t)
                .setHeader("Authorization", "Bearer " + apiKey)
                .setHeader("Accept", "application/json")
                .setHeader("Accept-Encoding", "gzip")
                .setHeader("User-Agent", userAgent);
        // Default headers come last, so a caller can override anything, Authorization included.
        defaultHeaders.forEach(b::setHeader);
        return new Call(p, b.build(), t.toMillis(), opts);
    }

    /**
     * The outcome of one attempt. {@code response} is null when the attempt failed without an HTTP
     * response ({@code failure}). The final response, success or error, goes to the hooks; retried
     * attempts and network failures do not.
     */
    private Step evaluate(Call call, int attempt, HttpResponse<byte[]> response, LifeAstroException failure) {
        if (response == null) {
            return attempt < maxRetries ? Step.retry(backoffMs(attempt, null)) : Step.fail(failure);
        }
        int status = response.statusCode();
        HttpHeaders headers = response.headers();
        JsonNode body = decode(response.body(), headers);
        String headerRequestId = headers.firstValue("x-request-id").filter(s -> !s.isEmpty()).orElse(null);
        if (status >= 200 && status < 300) {
            emit(call, status, headers);
            JsonNode value = call.options.isRaw() ? body : unwrap(body);
            return Step.done(value, body, status, headerRequestId);
        }
        Duration retryAfter = parseRetryAfter(headers.firstValue("retry-after").orElse(null), Instant.now());
        LifeAstroException error = LifeAstroException.fromResponse(status, body, retryAfter, headerRequestId);
        if (RETRYABLE_STATUS.contains(status) && attempt < maxRetries) {
            return Step.retry(backoffMs(attempt, retryAfter));
        }
        emit(call, status, headers);
        return Step.fail(error);
    }

    /**
     * The API returns bare JSON today. A {@code { data, meta }} envelope is unwrapped if a response
     * ever carries one, so adding it on the server is not a breaking change.
     */
    private static JsonNode unwrap(JsonNode body) {
        return body != null && body.isObject() && body.has("data") ? body.get("data") : body;
    }

    private <T> T convert(Call call, Step step, Class<T> type) {
        JsonNode v = step.value;
        if (type == null || type == JsonNode.class) {
            @SuppressWarnings("unchecked")
            T node = (T) (v == null ? NullNode.getInstance() : v);
            return node;
        }
        if (v == null || v.isNull()) {
            return null;
        }
        if (JsonNode.class.isAssignableFrom(type) && type.isInstance(v)) {
            return type.cast(v);
        }
        try {
            return mapper.treeToValue(v, type);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new LifeAstroException(
                    "Could not read the response of " + call.path + " as " + type.getName() + ": " + e.getMessage(),
                    "invalid_response", step.status, step.requestId, step.body, e);
        }
    }

    private void emit(Call call, int status, HttpHeaders headers) {
        Consumer<ResponseInfo> perCall = call.options.getOnResponse();
        if (onResponse == null && perCall == null) {
            return;
        }
        ResponseInfo info = ResponseInfo.from(status, call.path, headers);
        invoke(onResponse, info);
        invoke(perCall, info);
    }

    private static void invoke(Consumer<ResponseInfo> hook, ResponseInfo info) {
        if (hook == null) {
            return;
        }
        try {
            hook.accept(info);
        } catch (RuntimeException e) {
            // A logging hook must never break the call.
        }
    }

    private JsonNode decode(byte[] raw, HttpHeaders headers) {
        if (raw == null || raw.length == 0) {
            return null;
        }
        byte[] bytes = decompress(raw, headers.firstValue("content-encoding").orElse(""));
        try {
            JsonNode node = reader.readTree(bytes);
            if (node != null && !node.isMissingNode()) {
                return node;
            }
        } catch (IOException e) {
            // Not JSON: hand the text back, as the Node.js SDK does.
        }
        return TextNode.valueOf(new String(bytes, StandardCharsets.UTF_8));
    }

    private static byte[] decompress(byte[] raw, String encoding) {
        String enc = encoding.trim().toLowerCase(Locale.ROOT);
        if (!enc.equals("gzip") && !enc.equals("deflate")) {
            return raw;
        }
        try (InputStream in = enc.equals("gzip")
                ? new GZIPInputStream(new ByteArrayInputStream(raw))
                : new InflaterInputStream(new ByteArrayInputStream(raw));
                ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length * 4)) {
            in.transferTo(out);
            return out.toByteArray();
        } catch (IOException e) {
            return raw;
        }
    }

    private static LifeAstroTimeoutException timedOut(Call call, Throwable cause) {
        return new LifeAstroTimeoutException("Request timed out after " + call.timeoutMs + "ms", cause);
    }

    /** A failure without an HTTP response. Programming errors are rethrown as they are. */
    private static LifeAstroException networkFailure(Throwable cause, Call call) {
        Throwable c = unwrap(cause);
        if (c instanceof HttpTimeoutException || c instanceof TimeoutException) {
            return timedOut(call, c);
        }
        if (c instanceof IOException) {
            String detail = c.getMessage() == null ? c.getClass().getSimpleName()
                    : c.getClass().getSimpleName() + ": " + c.getMessage();
            return new LifeAstroConnectionException("Network request failed (" + detail + ")", "connection_error", c);
        }
        if (c instanceof RuntimeException re) {
            throw re;
        }
        if (c instanceof Error err) {
            throw err;
        }
        return new LifeAstroConnectionException("Request failed (" + c + ")", "connection_error", c);
    }

    private static Throwable unwrap(Throwable t) {
        Throwable c = t;
        while ((c instanceof CompletionException || c instanceof ExecutionException) && c.getCause() != null) {
            c = c.getCause();
        }
        return c;
    }

    /**
     * The wait before retry number {@code attempt} (0-based): {@code Retry-After} capped at 30 s,
     * otherwise 0.5 s, 1 s, 2 s, ... up to 8 s, plus up to 0.25 s of jitter.
     */
    static long backoffMs(int attempt, Duration retryAfter) {
        if (retryAfter != null) {
            return Math.min(Math.max(retryAfter.toMillis(), 0), 30_000);
        }
        long base = Math.min(500L << Math.min(attempt, 10), 8_000L);
        return base + ThreadLocalRandom.current().nextLong(250);
    }

    /**
     * A {@code Retry-After} header: delta-seconds ({@code 120}) or an HTTP date
     * ({@code Wed, 21 Oct 2026 07:28:00 GMT}). Null when absent or unreadable.
     */
    static Duration parseRetryAfter(String value, Instant now) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String s = value.trim();
        try {
            double secs = Double.parseDouble(s);
            if (Double.isFinite(secs)) {
                return Duration.ofMillis(Math.round(Math.max(0, secs) * 1000));
            }
            return null;
        } catch (NumberFormatException e) {
            // Not a number: try the HTTP-date form.
        }
        try {
            Instant at = ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return Duration.ofMillis(Math.max(0, Duration.between(now, at).toMillis()));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("This LifeAstro client is closed");
        }
    }

    void close() {
        closed = true;
        if (ownsHttp && http instanceof AutoCloseable closeable) {
            // java.net.http.HttpClient is AutoCloseable from Java 21.
            try {
                closeable.close();
            } catch (Exception e) {
                // Nothing left to do.
            }
        }
    }
}
