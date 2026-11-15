package com.lifeastroapi.errors;

import com.fasterxml.jackson.databind.JsonNode;

/** HTTP 403: the key is valid but not allowed this call (plan, IP allow-list, suspended account). */
public class PermissionException extends LifeAstroException {
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
    public PermissionException(String message, String code, int status, String requestId, JsonNode body) {
        super(message, code, status, requestId, body, null);
    }
}
