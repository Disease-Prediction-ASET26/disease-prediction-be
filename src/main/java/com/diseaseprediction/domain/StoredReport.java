package com.diseaseprediction.domain;

import com.diseaseprediction.domain.enums.ReportStatus;
import com.diseaseprediction.persistence.EncryptedStringConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The report of one stored assessment, for a specialist (UC1 step 5, FR-D1). Created with the
 * assessment as {@link ReportStatus#DRAFT}. Sending it to a doctor, opening and reviewing it come
 * with the doctor portal (Iteration 12), so this iteration has no status transitions.
 *
 * <p>Named {@code StoredReport} because {@link Report} is the Iteration 2 analysis class.
 */
@Entity
@Table(name = "reports")
public class StoredReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false, updatable = false, unique = true)
    private StoredAssessment assessment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status = ReportStatus.DRAFT;

    /** The specialist the report was sent to; empty until it is sent. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
    private Doctor doctor;

    /** The doctor's notes after the review. Health data: encrypted. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "clinical_notes", columnDefinition = "TEXT")
    private String clinicalNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    /** For JPA only. */
    protected StoredReport() {}

    StoredReport(StoredAssessment assessment, OffsetDateTime createdAt) {
        this.assessment = assessment;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public StoredAssessment getAssessment() {
        return assessment;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public String getClinicalNotes() {
        return clinicalNotes;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }
}
