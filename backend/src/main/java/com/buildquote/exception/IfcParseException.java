package com.buildquote.exception;

/**
 * Exception thrown when IFC file parsing fails.
 */
public class IfcParseException extends RuntimeException {

    private final String pythonError;

    public IfcParseException(String message) {
        super(message);
        this.pythonError = null;
    }

    public IfcParseException(String message, String pythonError) {
        super(message);
        this.pythonError = pythonError;
    }

    public IfcParseException(String message, Throwable cause) {
        super(message, cause);
        this.pythonError = null;
    }

    public IfcParseException(String message, String pythonError, Throwable cause) {
        super(message, cause);
        this.pythonError = pythonError;
    }

    public String getPythonError() {
        return pythonError;
    }
}
