-- MDD Iteration 7: health-data persistence (see docs repo, mdd/iteration7/health_data_er.mmd).
-- Health data is stored as AES-256-GCM ciphertext written by the application (FieldEncryptor):
-- input, follow_up_state, result and clinical_notes each hold one encrypted JSON or text value.
-- Only ids, timestamps, statuses and counters are plaintext.
-- Both tables cascade from patients, so deleting an account removes its health data.
CREATE TABLE assessments (
    id               UUID        PRIMARY KEY,
    patient_id       UUID        NOT NULL REFERENCES patients (user_id) ON DELETE CASCADE,
    status           VARCHAR(20) NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    follow_up_rounds INTEGER     NOT NULL DEFAULT 0 CHECK (follow_up_rounds >= 0),
    input            TEXT        NOT NULL,
    follow_up_state  TEXT        NOT NULL,
    result           TEXT,
    version          BIGINT      NOT NULL DEFAULT 0,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_assessments_patient_created_at ON assessments (patient_id, created_at DESC);

-- One report per assessment. It stays DRAFT until reports can be sent to a doctor (Iteration 12).
CREATE TABLE reports (
    id             UUID        PRIMARY KEY,
    assessment_id  UUID        NOT NULL UNIQUE REFERENCES assessments (id) ON DELETE CASCADE,
    status         VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'SENT', 'OPENED', 'REVIEWED')),
    doctor_id      UUID        REFERENCES doctors (user_id) ON DELETE SET NULL,
    clinical_notes TEXT,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at        TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_reports_doctor_status ON reports (doctor_id, status);
