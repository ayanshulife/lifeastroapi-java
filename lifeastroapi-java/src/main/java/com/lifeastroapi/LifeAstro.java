package com.lifeastroapi;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The LifeAstroAPI client.
 *
 * <pre>{@code
 * LifeAstro client = LifeAstro.builder()
 *     .apiKey("dv_live_...")             // or set LIFEASTRO_API_KEY
 *     .build();
 *
 * JsonNode day = client.panchang().basic(PanchangBasicParams.builder()
 *     .lat(19.076).lon(72.8777).tz("Asia/Kolkata")
 *     .lang(Lang.GU)
 *     .build());
 *
 * JsonNode planets = client.chart().planets(ChartPlanetsParams.builder()
 *     .birth(BirthInput.of("1990-01-15", "10:30", "Asia/Kolkata", 19.076, 72.8777))
 *     .build());
 * }</pre>
 *
 * <p>Every resource group is an accessor ({@link #panchang()}, {@link #chart()},
 * {@link #milan()}, {@code western().natal()} and so on) with one method per endpoint. Each method
 * returns the response JSON as a Jackson {@link JsonNode}, or maps it to your own class with the
 * {@code Class<T>} overload. {@link #async()} has the same methods returning
 * {@link java.util.concurrent.CompletableFuture}.
 *
 * <p>The client is immutable and thread-safe: build one and share it across the application (a
 * Spring singleton bean, for example). {@link #close()} releases the HTTP client it created.
 */
public final class LifeAstro extends LifeAstroResources implements AutoCloseable {

    /** SDK version, sent in the {@code User-Agent} header. */
    public static final String VERSION = "0.2.0";

    /** The production API. */
    public static final String DEFAULT_BASE_URL = "https://api.lifeastroapi.com";

    /** The environment variable read when no API key is given. */
    public static final String API_KEY_ENV = "LIFEASTRO_API_KEY";

    /** Per-request timeout unless {@link Builder#timeout(Duration)} says otherwise. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /** Automatic retries unless {@link Builder#maxRetries(int)} says otherwise. */
    public static final int DEFAULT_MAX_RETRIES = 2;

    final HttpCore core;
    private final LifeAstroAsync async;

    private LifeAstro(HttpCore core) {
        this.core = core;
        this.async = new LifeAstroAsync(this);
    }

    /**
     * A new builder.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A client with this API key and the defaults for everything else.
     *
     * @param apiKey the API key ({@code dv_live_...})
     * @return the client
     */
    public static LifeAstro create(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    /**
     * A client with the key from the {@code LIFEASTRO_API_KEY} environment variable.
     *
     * @return the client
     * @throws IllegalStateException when the variable is not set
     */
    public static LifeAstro create() {
        return builder().build();
    }

    /**
     * The same resource groups and methods, returning {@link java.util.concurrent.CompletableFuture}.
     * Retries wait without blocking a thread.
     *
     * <pre>{@code
     * client.async().panchang().basic(params)
     *     .thenAccept(day -> System.out.println(day.path("tithi").path("name").asText()));
     * }</pre>
     *
     * @return the asynchronous view of this client
     */
    public LifeAstroAsync async() {
        return async;
    }

    /**
     * Calls any {@code /v1/*} endpoint directly, for endpoints this version does not wrap yet.
     *
     * @param path the path, e.g. {@code /v1/panchang/tithi}
     * @return the response JSON
     * @throws com.lifeastroapi.errors.LifeAstroException on an API error or a network failure
     */
    public JsonNode request(String path) {
        return core.send(path, null, JsonNode.class, null);
    }

    /**
     * Calls any {@code /v1/*} endpoint directly.
     *
     * <pre>{@code
     * JsonNode t = client.request("/v1/panchang/tithi",
     *     Map.of("lat", 28.61, "lon", 77.2, "tz", "Asia/Kolkata", "date", "2027-01-15"));
     * }</pre>
     *
     * @param path  the path, e.g. {@code /v1/panchang/tithi}
     * @param query query parameters by wire name; null and empty values are left out
     * @return the response JSON
     * @throws com.lifeastroapi.errors.LifeAstroException on an API error or a network failure
     */
    public JsonNode request(String path, Map<String, ?> query) {
        return core.send(path, query, JsonNode.class, null);
    }

    /**
     * Calls any {@code /v1/*} endpoint directly, with per-call options.
     *
     * @param path    the path
     * @param query   query parameters by wire name, or null
     * @param options per-call timeout, raw body or {@code onResponse} hook, or null
     * @return the response JSON
     * @throws com.lifeastroapi.errors.LifeAstroException on an API error or a network failure
     */
    public JsonNode request(String path, Map<String, ?> query, RequestOptions options) {
        return core.send(path, query, JsonNode.class, options);
    }

    /**
     * Calls any {@code /v1/*} endpoint directly and maps the response to {@code type}.
     *
     * @param <T>   the result type
     * @param path  the path
     * @param query query parameters by wire name, or null
     * @param type  the class to map the response to ({@link JsonNode} for the tree)
     * @return the mapped response
     * @throws com.lifeastroapi.errors.LifeAstroException on an API error, a network failure, or when
     *     the response does not map to {@code type} (code {@code invalid_response})
     */
    public <T> T request(String path, Map<String, ?> query, Class<T> type) {
        return core.send(path, query, type, null);
    }

    /**
     * Calls any {@code /v1/*} endpoint directly, maps the response to {@code type}, with per-call
     * options. Every generated method ends here.
     *
     * @param <T>     the result type
     * @param path    the path
     * @param query   query parameters by wire name, or null
     * @param type    the class to map the response to ({@link JsonNode} for the tree)
     * @param options per-call timeout, raw body or {@code onResponse} hook, or null
     * @return the mapped response
     * @throws com.lifeastroapi.errors.LifeAstroException on an API error, a network failure, or when
     *     the response does not map to {@code type} (code {@code invalid_response})
     */
    public <T> T request(String path, Map<String, ?> query, Class<T> type, RequestOptions options) {
        return core.send(path, query, Objects.requireNonNull(type, "type"), options);
    }

    /**
     * The API base URL this client calls.
     *
     * @return the base URL, without a trailing slash
     */
    public String baseUrl() {
        return core.baseUrl();
    }

    /**
     * The per-request timeout.
     *
     * @return the timeout
     */
    public Duration timeout() {
        return core.timeout();
    }

    /**
     * How many times a call is retried on 408, 425, 429, 5xx and network errors.
     *
     * @return the retry count
     */
    public int maxRetries() {
        return core.maxRetries();
    }

    /**
     * Marks the client closed and, on Java 21 and later, closes the HTTP client it created (an
     * {@link HttpClient} given to the builder is left open). Calls made afterwards throw
     * {@link IllegalStateException}.
     */
    @Override
    public void close() {
        core.close();
    }

    /** Shows the base URL and a masked key ({@code dv_live_****}), never the key itself. */
    @Override
    public String toString() {
        return "LifeAstro{baseUrl=" + core.baseUrl() + ", apiKey=" + core.maskedKey() + ", version=" + VERSION + "}";
    }

    /**
     * Builder for {@link LifeAstro}.
     *
     * <pre>{@code
     * LifeAstro client = LifeAstro.builder()
     *     .apiKey("dv_live_...")                  // or set LIFEASTRO_API_KEY
     *     .baseUrl("https://api.lifeastroapi.com") // override for a local server
     *     .timeout(Duration.ofSeconds(30))
     *     .maxRetries(2)
     *     .onResponse(info -> log.debug("{} credits left", info.creditsRemaining()))
     *     .build();
     * }</pre>
     */
    public static final class Builder {
        private String apiKey;
        private String baseUrl = DEFAULT_BASE_URL;
        private Duration timeout = DEFAULT_TIMEOUT;
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private Consumer<ResponseInfo> onResponse;
        private final Map<String, String> defaultHeaders = new LinkedHashMap<>();
        private HttpClient httpClient;
        private ObjectMapper objectMapper;
        private Function<String, String> env = System::getenv;

        private Builder() {
        }

        /**
         * The API key ({@code dv_live_...}). When not set, or blank, the {@code LIFEASTRO_API_KEY}
         * environment variable is used. Get one at https://lifeastroapi.com/dashboard/keys
         *
         * @param apiKey the key
         * @return this builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * The API base URL; default {@value LifeAstro#DEFAULT_BASE_URL}. Override it for a local
         * server ({@code http://localhost:8080}).
         *
         * @param baseUrl an http or https URL; a trailing slash is removed
         * @return this builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
            return this;
        }

        /**
         * The per-request timeout, from sending the request to the last byte of the body; default
         * 30 seconds. Also the connect timeout of the HTTP client the builder creates.
         *
         * @param timeout a positive duration
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            Objects.requireNonNull(timeout, "timeout");
            if (timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("timeout must be positive");
            }
            this.timeout = timeout;
            return this;
        }

        /**
         * Automatic retries on 408, 425, 429, 5xx and network errors, with exponential backoff
         * that honours {@code Retry-After}; default 2. A negative value counts as 0 (one attempt).
         *
         * @param maxRetries the number of retries
         * @return this builder
         */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = Math.max(0, maxRetries);
            return this;
        }

        /**
         * Called with the {@link ResponseInfo} of every call: request id, credits left, rate limit,
         * cache and language. Use it to log usage or warn when credits run low. Runs once per
         * final response, success or HTTP error, before a per-call hook; an exception it throws is
         * ignored.
         *
         * @param hook the hook, or null
         * @return this builder
         */
        public Builder onResponse(Consumer<ResponseInfo> hook) {
            this.onResponse = hook;
            return this;
        }

        /**
         * An extra header sent on every request. It wins over the SDK's own headers.
         *
         * @param name  the header name
         * @param value the header value
         * @return this builder
         */
        public Builder defaultHeader(String name, String value) {
            defaultHeaders.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
            return this;
        }

        /**
         * Extra headers sent on every request, added to those already set.
         *
         * @param headers header names and values
         * @return this builder
         */
        public Builder defaultHeaders(Map<String, String> headers) {
            headers.forEach(this::defaultHeader);
            return this;
        }

        /**
         * The {@link HttpClient} to send requests with, for a proxy, custom TLS or your own
         * executor. The client does not close it. By default the builder creates one that
         * follows redirects.
         *
         * @param httpClient the HTTP client
         * @return this builder
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
            return this;
        }

        /**
         * The Jackson {@link ObjectMapper} that parses responses and maps them to your classes
         * (the {@code Class<T>} overloads). In a Spring Boot application the starter passes the
         * application's mapper. By default: a mapper that ignores unknown properties and has the
         * Jackson modules found on the classpath.
         *
         * @param objectMapper the mapper
         * @return this builder
         */
        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
            return this;
        }

        /** Environment lookup, for tests. */
        Builder env(Function<String, String> env) {
            this.env = env;
            return this;
        }

        /**
         * The client.
         *
         * @return the client
         * @throws IllegalStateException when there is no API key
         * @throws IllegalArgumentException when the base URL is not an http(s) URL
         */
        public LifeAstro build() {
            String key = apiKey != null && !apiKey.isBlank() ? apiKey.trim() : env.apply(API_KEY_ENV);
            if (key == null || key.isBlank()) {
                throw new IllegalStateException("LifeAstro: an API key is required. Call apiKey(...) or set "
                        + API_KEY_ENV + ". Get one at https://lifeastroapi.com/dashboard/keys");
            }
            String url = baseUrl.trim().replaceAll("/+$", "");
            URI uri;
            try {
                uri = URI.create(url);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("baseUrl is not a valid URL: " + url, e);
            }
            if (uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException("baseUrl must be an http or https URL: " + url);
            }
            boolean owns = httpClient == null;
            HttpClient http = owns
                    ? HttpClient.newBuilder()
                            .connectTimeout(timeout)
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .build()
                    : httpClient;
            ObjectMapper mapper = objectMapper != null ? objectMapper : defaultMapper();
            HttpCore core = new HttpCore(key.trim(), url, timeout, maxRetries, http, owns,
                    "lifeastroapi-java/" + VERSION, defaultHeaders, onResponse, mapper);
            return new LifeAstro(core);
        }

        private static ObjectMapper defaultMapper() {
            ObjectMapper mapper = JsonMapper.builder()
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build();
            mapper.findAndRegisterModules();
            return mapper;
        }
    }
}
