package com.buildquote.exception;

/**
 * Exception thrown when IFC processing exceeds the configured timeout.
 */
public class IfcProcessingTimeoutException extends RuntimeException {

    private final int timeoutSeconds;

    public IfcProcessingTimeoutException(int timeoutSeconds) {
        super("IFC processing timed out after " + timeoutSeconds + " seconds");
        this.timeoutSeconds = timeoutSeconds;
    }

    public IfcProcessingTimeoutException(String message, int timeoutSeconds) {
        super(message);
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }
}
