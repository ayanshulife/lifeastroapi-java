package com.lifeastroapi.errors;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;

/**
 * HTTP 429: the per-minute rate limit or the daily quota is used up. Retried automatically
 * (honouring {@code Retry-After}) before this is thrown.
 */
public class RateLimitException extends LifeAstroException {
    private static final long serialVersionUID = 1L;

    private final Duration retryAfter;

    /**
     * Creates the exception.
     *
     * @param message    the API's message
     * @param code       the API's error code
     * @param status     the HTTP status (429)
     * @param requestId  the request id, or null
     * @param body       the parsed response body, or null
     * @param retryAfter the {@code Retry-After} wait, or null
     */
    public RateLimitException(String message, String code, int status, String requestId, JsonNode body,
            Duration retryAfter) {
        super(message, code, status, requestId, body, null);
        this.retryAfter = retryAfter;
    }

    /**
     * How long to wait before trying again, from the {@code Retry-After} header (seconds or an
     * HTTP date).
     *
     * @return the wait, or null when the API sent none
     */
    public Duration retryAfter() {
        return retryAfter;
    }
}
