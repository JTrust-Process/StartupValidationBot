package com.startupvalidationbot.radar.source;

public class SourceFetchException extends Exception {
    public SourceFetchException(String message) {
        super(message);
    }

    public SourceFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
