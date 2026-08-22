package com.secphils.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * RFC 6238 TOTP (time-based one-time password) with a base32 secret, ready for
 * any standard authenticator app (Google Authenticator, Authy, 1Password, ...).
 * No external TOTP library — HMAC-SHA1 comes from the JDK.
 */
@Service
public class TwoFactorService {

    private static final Logger log = LoggerFactory.getLogger(TwoFactorService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SECRET_BYTES = 20; // 160-bit -> 32 base32 chars
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final long STEP_SECONDS = 30L;
    // Accept the code for the current 30s window plus one either side (clock drift).
    private static final int WINDOW = 1;
    private static final String ISSUER = "SECPhils";

    /** Generate a fresh 32-char base32 secret (no padding). */
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(ALPHABET.charAt((buffer >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(ALPHABET.charAt((buffer << (5 - bits)) & 0x1F));
        }
        return sb.toString();
    }

    /** otpauth:// URI for the user's QR / manual entry. */
    public String otpauthUri(String email, String secret) {
        return "otpauth://totp/" + urlEncode(ISSUER) + ":" + urlEncode(email == null ? "" : email)
                + "?secret=" + secret + "&issuer=" + urlEncode(ISSUER) + "&algorithm=SHA1&digits=6&period=30";
    }

    /** Verify a 6-digit code against the secret within the drift window. */
    public boolean verify(String secret, String code) {
        if (secret == null || code == null || code.length() != 6) {
            return false;
        }
        try {
            byte[] key = base32Decode(secret.trim().replace(" ", "").toUpperCase());
            if (key.length == 0) return false;
            long counter = System.currentTimeMillis() / 1000 / STEP_SECONDS;
            int expected;
            try {
                expected = Integer.parseInt(code.trim());
            } catch (NumberFormatException e) {
                return false;
            }
            for (int offset = -WINDOW; offset <= WINDOW; offset++) {
                if (constantTimeEquals(totp(key, counter + offset), expected)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("TOTP verification failed: {}", e.getMessage());
            return false;
        }
    }

    private static int totp(byte[] key, long counter) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, "HmacSHA1"));
        byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        return binary % 1_000_000;
    }

    private static boolean constantTimeEquals(int a, int b) {
        return (a ^ b) == 0;
    }

    private static byte[] base32Decode(String input) {
        int[] table = new int[128];
        java.util.Arrays.fill(table, -1);
        for (int i = 0; i < ALPHABET.length(); i++) {
            table[ALPHABET.charAt(i)] = i;
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : input.toCharArray()) {
            if (c == '=') break;
            int v = c < 128 ? table[c] : -1;
            if (v < 0) {
                // Invalid character — tolerate common OCR/lowercase noise only for single chars.
                if (!Character.isLetterOrDigit(c)) continue;
                return new byte[0];
            }
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    private static String urlEncode(String s) {
        return s == null ? "" : Base64.getUrlEncoder().withoutPadding()
                .encodeToString(s.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .replace("_", "");
    }
}