package com.buildquote.exception;

/**
 * Exception thrown when an IFC file exceeds the maximum allowed size.
 */
public class IfcFileTooLargeException extends RuntimeException {

    private final long fileSizeMb;
    private final int maxSizeMb;

    public IfcFileTooLargeException(long fileSizeMb, int maxSizeMb) {
        super("File size " + fileSizeMb + "MB exceeds maximum allowed " + maxSizeMb + "MB");
        this.fileSizeMb = fileSizeMb;
        this.maxSizeMb = maxSizeMb;
    }

    public long getFileSizeMb() {
        return fileSizeMb;
    }

    public int getMaxSizeMb() {
        return maxSizeMb;
    }
}
