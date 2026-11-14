package com.diseaseprediction.persistence;

import com.diseaseprediction.security.FieldEncryptor;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDate;

/**
 * Stores a LocalDate encrypted with {@link FieldEncryptor} (ISO-8601 text before encryption). Use
 * with {@code @Convert}. Encrypted dates cannot be compared or sorted in SQL.
 */
@Converter
public class EncryptedLocalDateConverter implements AttributeConverter<LocalDate, String> {
    private final FieldEncryptor encryptor;

    public EncryptedLocalDateConverter(FieldEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public String convertToDatabaseColumn(LocalDate attribute) {
        return attribute == null ? null : encryptor.encrypt(attribute.toString());
    }

    @Override
    public LocalDate convertToEntityAttribute(String dbData) {
        return dbData == null ? null : LocalDate.parse(encryptor.decrypt(dbData));
    }
}
