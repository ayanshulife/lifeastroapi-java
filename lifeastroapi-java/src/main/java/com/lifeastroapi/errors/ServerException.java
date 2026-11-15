package com.lifeastroapi.errors;

import com.fasterxml.jackson.databind.JsonNode;

/** HTTP 5xx: the API failed. Retried automatically before this is thrown. */
public class ServerException extends LifeAstroException {
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
    public ServerException(String message, String code, int status, String requestId, JsonNode body) {
        super(message, code, status, requestId, body, null);
    }
}
