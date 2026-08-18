package com.startupvalidationbot.radar.ai;

public class RadarAiException extends RuntimeException {
    private final String errorType;
    private final boolean retryable;
    private final int attempts;

    public RadarAiException(String errorType, String message, boolean retryable, int attempts) {
        super(message);
        this.errorType = errorType;
        this.retryable = retryable;
        this.attempts = attempts;
    }

    public RadarAiException(String errorType, String message, boolean retryable, int attempts, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.retryable = retryable;
        this.attempts = attempts;
    }

    public String errorType() {
        return errorType;
    }

    public boolean retryable() {
        return retryable;
    }

    public int attempts() {
        return attempts;
    }
}
