package com.diseaseprediction.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.crypto.KeyGenerator;

class FieldEncryptorTest {

    private final FieldEncryptor encryptor = new FieldEncryptor(newKey());

    @Test
    void decryptReturnsTheOriginalValue() {
        String stored = encryptor.encrypt("Ana Pop");

        assertThat(stored).startsWith("v1:").doesNotContain("Ana");
        assertThat(encryptor.decrypt(stored)).isEqualTo("Ana Pop");
    }

    @Test
    void sameValueEncryptsDifferentlyEachTime() {
        assertThat(encryptor.encrypt("Ana Pop")).isNotEqualTo(encryptor.encrypt("Ana Pop"));
    }

    @Test
    void nullStaysNull() {
        assertThat(encryptor.encrypt(null)).isNull();
        assertThat(encryptor.decrypt(null)).isNull();
    }

    @Test
    void tamperedValueIsRejected() {
        String stored = encryptor.encrypt("Ana Pop");
        byte[] bytes = Base64.getDecoder().decode(stored.substring("v1:".length()));
        bytes[bytes.length - 1] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> encryptor.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("Ana");
    }

    @Test
    void valueEncryptedWithAnotherKeyIsRejected() {
        String stored = new FieldEncryptor(newKey()).encrypt("Ana Pop");

        assertThatThrownBy(() -> encryptor.decrypt(stored))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingOrInvalidKeyStopsStartup() {
        assertThatThrownBy(() -> new FieldEncryptor(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATA_ENCRYPTION_KEY");
        assertThatThrownBy(() -> new FieldEncryptor("not base64!"))
                .isInstanceOf(IllegalStateException.class);
        String sixteenBytes = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new FieldEncryptor(sixteenBytes))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    private static String newKey() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            return Base64.getEncoder().encodeToString(generator.generateKey().getEncoded());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
