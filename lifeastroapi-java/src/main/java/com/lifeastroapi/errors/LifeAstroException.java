package com.lifeastroapi.errors;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;

/**
 * Base class of every exception the SDK throws for a failed call. Unchecked.
 *
 * <p>Every non-2xx response becomes a subclass for its status, carrying the API's error
 * {@link #code()}, the HTTP {@link #status()} and the {@link #requestId()} to quote to support:
 *
 * <ul>
 *   <li>{@link BadRequestException} (400), {@link AuthenticationException} (401),
 *       {@link PaymentRequiredException} (402), {@link PermissionException} (403),
 *       {@link NotFoundException} (404), {@link RateLimitException} (429, with
 *       {@link RateLimitException#retryAfter()}), {@link ServerException} (5xx);</li>
 *   <li>{@link LifeAstroConnectionException} (network failure, status 0) and its subclass
 *       {@link LifeAstroTimeoutException} (the call took longer than the timeout).</li>
 * </ul>
 */
public class LifeAstroException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String code;
    private final int status;
    private final String requestId;
    private final transient JsonNode body;

    /**
     * Creates the exception.
     *
     * @param message   a readable message (the API's own message for HTTP errors)
     * @param code      a machine-readable code, e.g. {@code insufficient_credits}
     * @param status    the HTTP status, 0 for network failures
     * @param requestId the API request id, or null
     * @param body      the parsed response body, or null
     * @param cause     the underlying exception, or null
     */
    public LifeAstroException(String message, String code, int status, String requestId, JsonNode body,
            Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
        this.requestId = requestId;
        this.body = body;
    }

    /**
     * The machine-readable error code, e.g. {@code insufficient_credits}, {@code rate_limited},
     * {@code timeout}.
     *
     * @return the code
     */
    public String code() {
        return code;
    }

    /**
     * The HTTP status; 0 for network failures and timeouts.
     *
     * @return the status
     */
    public int status() {
        return status;
    }

    /**
     * The API request id ({@code request_id} of the error body, else the {@code X-Request-ID}
     * header). Quote it to support.
     *
     * @return the request id, or null
     */
    public String requestId() {
        return requestId;
    }

    /**
     * The parsed response body, when there was one.
     *
     * @return the body, or null
     */
    public JsonNode body() {
        return body;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder(getClass().getName()).append(": ").append(getMessage());
        s.append(" (");
        if (status != 0) {
            s.append(status).append(' ');
        }
        s.append(code);
        if (requestId != null) {
            s.append(", request ").append(requestId);
        }
        return s.append(')').toString();
    }

    /**
     * The exception for an error response: the subclass for its status, with the code, message
     * and request id of the API's {@code { "error": { code, message, request_id } }} body.
     *
     * @param status          the HTTP status
     * @param body            the parsed body, or null
     * @param retryAfter      the {@code Retry-After} wait, or null
     * @param headerRequestId the {@code X-Request-ID} header, used when the body has no id
     * @return the exception to throw
     */
    public static LifeAstroException fromResponse(int status, JsonNode body, Duration retryAfter,
            String headerRequestId) {
        JsonNode error = body != null && body.isObject() ? body.path("error") : null;
        String code = text(error, "code");
        String message = text(error, "message");
        String requestId = text(error, "request_id");
        if (code == null) {
            code = defaultCode(status);
        }
        if (message == null) {
            message = "HTTP " + status;
        }
        if (requestId == null) {
            requestId = headerRequestId;
        }
        switch (status) {
            case 400:
                return new BadRequestException(message, code, status, requestId, body);
            case 401:
                return new AuthenticationException(message, code, status, requestId, body);
            case 402:
                return new PaymentRequiredException(message, code, status, requestId, body);
            case 403:
                return new PermissionException(message, code, status, requestId, body);
            case 404:
                return new NotFoundException(message, code, status, requestId, body);
            case 429:
                return new RateLimitException(message, code, status, requestId, body, retryAfter);
            default:
                if (status >= 500) {
                    return new ServerException(message, code, status, requestId, body);
                }
                return new LifeAstroException(message, code, status, requestId, body, null);
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode v = node.get(field);
        return v != null && v.isTextual() ? v.asText() : null;
    }

    private static String defaultCode(int status) {
        if (status == 0) {
            return "connection_error";
        }
        if (status >= 500) {
            return "internal_error";
        }
        if (status >= 400) {
            return "bad_request";
        }
        return "error";
    }
}
