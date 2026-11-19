package com.uniminuto.clinica.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Password storage utilities. New accounts use PBKDF2-HMAC-SHA256; MD5 is read-only legacy support. */
public final class PasswordUtil {
    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() { }

    /** Kept for source compatibility; despite its legacy name, new values use PBKDF2. */
    public static String generarMD5(String password) {
        return hash(password);
    }

    /** Kept for source compatibility; despite its legacy name, new values use PBKDF2. */
    public static String generateMD5(String password) {
        return hash(password);
    }

    public static String hash(String password) {
        if (password == null || password.isEmpty()) return "";
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(password.toCharArray(), salt, ITERATIONS);
        return "PBKDF2$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(derived);
    }

    public static boolean matches(String password, String stored) {
        if (password == null || stored == null || stored.isBlank()) return false;
        try {
            if (stored.startsWith("PBKDF2$")) {
                String[] parts = stored.split("\\$", -1);
                if (parts.length != 4) return false;
                int iterations = Integer.parseInt(parts[1]);
                byte[] salt = Base64.getDecoder().decode(parts[2]);
                byte[] expected = Base64.getDecoder().decode(parts[3]);
                byte[] actual = derive(password.toCharArray(), salt, iterations);
                return MessageDigest.isEqual(expected, actual);
            }
            // Backward-compatible read path only. Successful legacy logins should be re-hashed.
            String legacyMd5 = HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(password.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(legacyMd5.getBytes(StandardCharsets.US_ASCII), stored.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible proteger la contraseña", e);
        } finally {
            spec.clearPassword();
        }
    }
}
