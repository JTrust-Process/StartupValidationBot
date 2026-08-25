package com.startupvalidationbot.radar;

public class InvalidCompanyIdentityException extends IllegalArgumentException {
    public InvalidCompanyIdentityException(String message) {
        super(message);
    }
}
