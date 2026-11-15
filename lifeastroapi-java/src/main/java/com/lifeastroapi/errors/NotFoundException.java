package com.lifeastroapi.errors;

import com.fasterxml.jackson.databind.JsonNode;

/** HTTP 404: unknown path or sub-resource (for example an unknown remedy id). */
public class NotFoundException extends LifeAstroException {
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
    public NotFoundException(String message, String code, int status, String requestId, JsonNode body) {
        super(message, code, status, requestId, body, null);
    }
}
