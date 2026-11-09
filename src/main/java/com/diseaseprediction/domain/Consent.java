package com.diseaseprediction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * One grant or revoke of consent for health-data processing (FR-G1). Rows are never updated, so the
 * table is an auditable history; the latest row is the patient's current consent.
 */
@Entity
@Table(name = "consents")
public class Consent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false)
    private Patient patient;

    @Column(nullable = false, updatable = false)
    private boolean granted;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private OffsetDateTime recordedAt;

    /** For JPA only. */
    protected Consent() {}

    /**
     * Created through {@link Patient#giveConsent()} / {@link Patient#revokeConsent()}. The time is
     * kept strictly after the previous entry so the history order is never ambiguous.
     */
    Consent(Patient patient, boolean granted, Consent previous) {
        this.patient = patient;
        this.granted = granted;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        if (previous != null && !now.isAfter(previous.recordedAt)) {
            now = previous.recordedAt.plus(1, ChronoUnit.MICROS);
        }
        this.recordedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public boolean isGranted() {
        return granted;
    }

    public OffsetDateTime getRecordedAt() {
        return recordedAt;
    }
}
