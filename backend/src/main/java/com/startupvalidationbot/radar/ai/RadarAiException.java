package com.startupvalidationbot.radar.ai;

public class RadarAiException extends RuntimeException {
    private final String errorType;
    private final boolean retryable;
    private final int attempts;
    private final Integer httpStatus;
    private final String providerErrorType;
    private final String providerErrorCode;

    public RadarAiException(String errorType, String message, boolean retryable, int attempts) {
        this(errorType, message, retryable, attempts, null, null, null, null);
    }

    public RadarAiException(String errorType, String message, boolean retryable, int attempts, Throwable cause) {
        this(errorType, message, retryable, attempts, null, null, null, cause);
    }

    public RadarAiException(String errorType, String message, boolean retryable, int attempts,
            Integer httpStatus, String providerErrorType, String providerErrorCode) {
        this(errorType, message, retryable, attempts, httpStatus, providerErrorType, providerErrorCode, null);
    }

    private RadarAiException(String errorType, String message, boolean retryable, int attempts,
            Integer httpStatus, String providerErrorType, String providerErrorCode, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.retryable = retryable;
        this.attempts = attempts;
        this.httpStatus = httpStatus;
        this.providerErrorType = providerErrorType;
        this.providerErrorCode = providerErrorCode;
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

    public Integer httpStatus() {
        return httpStatus;
    }

    public String providerErrorType() {
        return providerErrorType;
    }

    public String providerErrorCode() {
        return providerErrorCode;
    }
}
