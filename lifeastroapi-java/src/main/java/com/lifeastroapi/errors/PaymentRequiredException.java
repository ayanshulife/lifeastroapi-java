package com.lifeastroapi.errors;

import com.fasterxml.jackson.databind.JsonNode;

/** HTTP 402: out of credits, or the trial is used up (code such as {@code insufficient_credits}). */
public class PaymentRequiredException extends LifeAstroException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message   the API's message
     * @param code      the API's error code
     * @param status    the HTTP status
     * @param requestId the request id, or null
     * @param body      the parsed response body, or null
     */
    public PaymentRequiredException(String message, String code, int status, String requestId, JsonNode body) {
        super(message, code, status, requestId, body, null);
    }
}
