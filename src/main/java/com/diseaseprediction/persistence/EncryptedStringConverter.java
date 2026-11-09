package com.diseaseprediction.persistence;

import com.diseaseprediction.security.FieldEncryptor;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores a String attribute encrypted with {@link FieldEncryptor}. Use with {@code @Convert}. */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {
    private final FieldEncryptor encryptor;

    public EncryptedStringConverter(FieldEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return encryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return encryptor.decrypt(dbData);
    }
}
