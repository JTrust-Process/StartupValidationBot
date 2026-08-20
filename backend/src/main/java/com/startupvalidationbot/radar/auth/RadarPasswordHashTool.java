package com.startupvalidationbot.radar.auth;

import java.io.Console;
import java.util.Arrays;

public final class RadarPasswordHashTool {
    private RadarPasswordHashTool() {
    }

    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("Run this command from an interactive terminal");
        }
        char[] password = console.readPassword("Radar admin password: ");
        char[] confirmation = console.readPassword("Confirm password: ");
        try {
            if (password.length < 12) throw new IllegalArgumentException("Use at least 12 characters");
            if (!Arrays.equals(password, confirmation)) throw new IllegalArgumentException("Passwords do not match");
            console.writer().println(RadarPasswordHasher.hash(password));
            console.writer().flush();
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
        }
    }
}
