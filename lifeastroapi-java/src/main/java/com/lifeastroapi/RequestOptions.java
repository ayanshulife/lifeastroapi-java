package com.lifeastroapi;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Options for one call: a timeout, the raw body, or an {@code onResponse} hook. Pass it as the
 * last argument of any method.
 *
 * <pre>{@code
 * RequestOptions opts = RequestOptions.builder()
 *     .timeout(Duration.ofSeconds(5))
 *     .onResponse(info -> log.info("{} credits left", info.creditsRemaining()))
 *     .build();
 * client.panchang().basic(params, opts);
 * }</pre>
 *
 * <p>Immutable and thread-safe; one instance can be shared.
 */
public final class RequestOptions {

    private static final RequestOptions NONE = new RequestOptions(new Builder());

    private final Duration timeout;
    private final boolean raw;
    private final Consumer<ResponseInfo> onResponse;

    private RequestOptions(Builder b) {
        this.timeout = b.timeout;
        this.raw = b.raw;
        this.onResponse = b.onResponse;
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
     * Options that change nothing.
     *
     * @return the empty options
     */
    public static RequestOptions none() {
        return NONE;
    }

    /**
     * Options with only a timeout.
     *
     * @param timeout the timeout for this call, instead of the client's
     * @return the options
     */
    public static RequestOptions timeout(Duration timeout) {
        return builder().timeout(timeout).build();
    }

    /**
     * Options with only an {@code onResponse} hook.
     *
     * @param hook called with this call's {@link ResponseInfo}, after the client-level hook
     * @return the options
     */
    public static RequestOptions onResponse(Consumer<ResponseInfo> hook) {
        return builder().onResponse(hook).build();
    }

    /**
     * The timeout for this call, or null to use the client's.
     *
     * @return the timeout, or null
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * Whether the response body is returned exactly as received, without unwrapping a
     * {@code { data, meta }} envelope.
     *
     * @return true for the raw body
     */
    public boolean isRaw() {
        return raw;
    }

    /**
     * The per-call hook, or null.
     *
     * @return the hook, or null
     */
    public Consumer<ResponseInfo> getOnResponse() {
        return onResponse;
    }

    /** Builder for {@link RequestOptions}. */
    public static final class Builder {
        private Duration timeout;
        private boolean raw;
        private Consumer<ResponseInfo> onResponse;

        private Builder() {
        }

        /**
         * The timeout for this call, instead of the client's.
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
         * Return the body exactly as received, without unwrapping a {@code { data, meta }}
         * envelope if one is present.
         *
         * @param raw true for the raw body
         * @return this builder
         */
        public Builder raw(boolean raw) {
            this.raw = raw;
            return this;
        }

        /**
         * Called with this call's {@link ResponseInfo}, after the client-level hook. An exception
         * it throws is ignored.
         *
         * @param hook the hook, or null
         * @return this builder
         */
        public Builder onResponse(Consumer<ResponseInfo> hook) {
            this.onResponse = hook;
            return this;
        }

        /**
         * The options.
         *
         * @return the immutable options
         */
        public RequestOptions build() {
            return new RequestOptions(this);
        }
    }
}
