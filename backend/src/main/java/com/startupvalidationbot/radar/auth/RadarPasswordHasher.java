package com.startupvalidationbot.radar.auth;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class RadarPasswordHasher {
    private static final String PREFIX = "pbkdf2-sha256";
    private static final int DEFAULT_ITERATIONS = 310_000;
    private static final int KEY_LENGTH_BITS = 256;

    private RadarPasswordHasher() {
    }

    public static String hash(char[] password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] derived = derive(password, salt, DEFAULT_ITERATIONS);
        return PREFIX + "$" + DEFAULT_ITERATIONS + "$"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(salt) + "$"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(derived);
    }

    public static boolean verify(char[] password, String encoded) {
        if (encoded == null || encoded.isBlank()) return false;
        try {
            String[] parts = encoded.split("\\$", -1);
            if (parts.length != 4 || !PREFIX.equals(parts[0])) return false;
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 100_000 || iterations > 2_000_000) return false;
            byte[] salt = Base64.getUrlDecoder().decode(parts[2]);
            byte[] expected = Base64.getUrlDecoder().decode(parts[3]);
            if (salt.length < 16 || expected.length != KEY_LENGTH_BITS / 8) return false;
            return MessageDigest.isEqual(expected, derive(password, salt, iterations));
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("PBKDF2-SHA256 is unavailable", error);
        } finally {
            spec.clearPassword();
        }
    }
}
