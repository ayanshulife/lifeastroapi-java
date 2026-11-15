package com.lifeastroapi.errors;

/**
 * The request did not get an HTTP response: DNS failure, refused or reset connection, or (code
 * {@code aborted}) the calling thread was interrupted. {@link #status()} is 0. Network failures are
 * retried automatically before this is thrown.
 */
public class LifeAstroConnectionException extends LifeAstroException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what happened
     * @param code    {@code connection_error}, {@code timeout} or {@code aborted}
     * @param cause   the underlying exception, or null
     */
    public LifeAstroConnectionException(String message, String code, Throwable cause) {
        super(message, code, 0, null, null, cause);
    }
}
