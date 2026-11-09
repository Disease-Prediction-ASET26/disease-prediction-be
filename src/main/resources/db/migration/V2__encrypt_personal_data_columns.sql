-- MDD Iteration 5 refinement: field-level encryption of personal data (GDPR, NFR-1).
-- users.full_name and patients.date_of_birth now hold AES-256-GCM ciphertext written by the
-- application (FieldEncryptor), stored as text: "v1:" + Base64(IV || ciphertext || tag).
--
-- Existing plaintext cannot be encrypted in SQL because the key only exists in the
-- application. Stop instead of silently corrupting data if any users already exist.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM users) THEN
        RAISE EXCEPTION 'V2 cannot encrypt existing rows in SQL; migrate existing users first';
    END IF;
END
$$;

-- full_name: up to 255 characters of plaintext become at most ~1,400 characters of ciphertext.
ALTER TABLE users ALTER COLUMN full_name TYPE VARCHAR(2048);

-- date_of_birth: the table is empty (checked above), so no value is lost by USING NULL.
ALTER TABLE patients ALTER COLUMN date_of_birth TYPE VARCHAR(128) USING NULL;
