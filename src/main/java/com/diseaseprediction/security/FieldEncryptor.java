package com.diseaseprediction.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts personal-data columns before they reach the database (field-level encryption, GDPR
 * NFR-1). Uses AES-256-GCM: a random 12-byte IV per value, so equal values encrypt differently, and
 * a 128-bit authentication tag, so tampered values are rejected on read.
 *
 * <p>Stored format: {@code v1:} + Base64(IV || ciphertext || tag). The version prefix allows a
 * later key rotation. The key comes from {@code app.encryption.key} ({@code DATA_ENCRYPTION_KEY} in
 * {@code .env} locally, a secret store in production) and is never logged.
 */
@Component
public class FieldEncryptor {
    private static final String VERSION_PREFIX = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public FieldEncryptor(@Value("${app.encryption.key:}") String base64Key) {
        this.key = parseKey(base64Key);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] stored =
                    ByteBuffer.allocate(iv.length + ciphertext.length)
                            .put(iv)
                            .put(ciphertext)
                            .array();
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(stored);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Field encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(VERSION_PREFIX)) {
            throw new IllegalStateException("Encrypted value has an unknown format");
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(stored.substring(VERSION_PREFIX.length()));
            if (bytes.length <= IV_BYTES) {
                throw new IllegalStateException("Encrypted value is too short");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES));
            byte[] plaintext = cipher.doFinal(bytes, IV_BYTES, bytes.length - IV_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Wrong key or modified data. The message deliberately contains no data or key.
            throw new IllegalStateException("Could not decrypt value (wrong key or tampered)", e);
        }
    }

    private static SecretKey parseKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "app.encryption.key is not set. Set DATA_ENCRYPTION_KEY in .env"
                            + " (generate one with: openssl rand -base64 32)");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.encryption.key is not valid Base64", e);
        }
        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalStateException(
                    "app.encryption.key must be "
                            + KEY_BYTES
                            + " bytes (AES-256), got "
                            + keyBytes.length);
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
