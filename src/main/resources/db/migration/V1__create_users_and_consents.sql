-- MDD Iteration 5: users and consent (see docs repo, mdd/iteration5/user_consent_er.mmd).
-- JOINED inheritance: users holds the shared fields; each user type has its own table that
-- shares the users id. Deleting a users row cascades to its type row and its consent history.

CREATE TABLE users (
    id          UUID         PRIMARY KEY,
    identity_id VARCHAR(255) NOT NULL UNIQUE,  -- Keycloak user id ("sub" claim of the token)
    email       VARCHAR(320) NOT NULL,
    full_name   VARCHAR(255) NOT NULL,
    role        VARCHAR(20)  NOT NULL CHECK (role IN ('PATIENT', 'DOCTOR', 'ADMIN')),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE patients (
    user_id       UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    date_of_birth DATE
);

CREATE TABLE doctors (
    user_id        UUID         PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    specialty      VARCHAR(100) NOT NULL,
    license_number VARCHAR(64)  NOT NULL UNIQUE,
    is_verified    BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE administrators (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE
);

-- Append-only consent history (FR-G1): one row per grant or revoke; the latest row is current.
CREATE TABLE consents (
    id          UUID    PRIMARY KEY,
    patient_id  UUID    NOT NULL REFERENCES patients (user_id) ON DELETE CASCADE,
    granted     BOOLEAN NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_consents_patient_recorded_at ON consents (patient_id, recorded_at);
