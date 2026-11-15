package com.lifeastroapi.errors;

/**
 * The call took longer than its timeout (code {@code timeout}, {@link #status()} 0). Retried
 * automatically before this is thrown.
 */
public class LifeAstroTimeoutException extends LifeAstroConnectionException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what happened, e.g. {@code Request timed out after 30000ms}
     * @param cause   the underlying exception, or null
     */
    public LifeAstroTimeoutException(String message, Throwable cause) {
        super(message, "timeout", cause);
    }
}
