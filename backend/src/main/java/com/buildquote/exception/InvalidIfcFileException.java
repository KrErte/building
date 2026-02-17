package com.buildquote.exception;

/**
 * Exception thrown when an uploaded file is not a valid IFC file.
 */
public class InvalidIfcFileException extends RuntimeException {

    public InvalidIfcFileException(String message) {
        super(message);
    }

    public InvalidIfcFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
