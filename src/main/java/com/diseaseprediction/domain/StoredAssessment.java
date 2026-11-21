package com.diseaseprediction.domain;

import com.diseaseprediction.domain.enums.AssessmentStatus;
import com.diseaseprediction.persistence.EncryptedStringConverter;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.Version;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * One assessment a patient ran, stored with its owner (MDD Iteration 7). The input, the follow-up
 * state and the latest result are health data: each is one JSON document, stored encrypted. The
 * JSON is written and read by the service layer; this entity only holds the strings.
 *
 * <p>Named {@code StoredAssessment} because {@link Assessment} is the Iteration 2 analysis class
 * used by the design-pattern demos.
 */
@Entity
@Table(name = "assessments")
public class StoredAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false)
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssessmentStatus status;

    @Column(name = "follow_up_rounds", nullable = false)
    private int followUpRounds;

    /** Free text and symptom entries as submitted. Health data: encrypted. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String input;

    /** Confirmed and denied symptoms and the answers so far. Health data: encrypted. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "follow_up_state", nullable = false, columnDefinition = "TEXT")
    private String followUpState;

    /** The latest prediction result as returned to the patient. Health data: encrypted. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String result;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToOne(mappedBy = "assessment", cascade = CascadeType.ALL, optional = false)
    private StoredReport report;

    /** For JPA only. */
    protected StoredAssessment() {}

    /**
     * A new assessment with its first result and a draft report. {@code followUpRounds} is 0 for a
     * new assessment; older clients that run the follow-up loop without an id start higher.
     */
    public StoredAssessment(
            Patient patient,
            String input,
            String followUpState,
            int followUpRounds,
            String result,
            boolean completed) {
        if (followUpRounds < 0) {
            throw new IllegalArgumentException("followUpRounds must not be negative");
        }
        this.followUpRounds = followUpRounds;
        this.patient = Objects.requireNonNull(patient, "patient");
        this.input = Objects.requireNonNull(input, "input");
        this.followUpState = Objects.requireNonNull(followUpState, "followUpState");
        this.result = result;
        this.status = completed ? AssessmentStatus.COMPLETED : AssessmentStatus.IN_PROGRESS;
        this.createdAt = now();
        this.updatedAt = createdAt;
        this.report = new StoredReport(this, createdAt);
    }

    /**
     * Records one answered follow-up question and the result it produced.
     *
     * @throws IllegalStateException if the assessment is already completed
     */
    public void recordFollowUp(String followUpState, String result, boolean completed) {
        if (isCompleted()) {
            throw new IllegalStateException("The assessment is already completed.");
        }
        this.followUpState = Objects.requireNonNull(followUpState, "followUpState");
        this.result = result;
        this.followUpRounds++;
        this.status = completed ? AssessmentStatus.COMPLETED : AssessmentStatus.IN_PROGRESS;
        OffsetDateTime now = now();
        this.updatedAt = now.isAfter(updatedAt) ? now : updatedAt.plus(1, ChronoUnit.MICROS);
    }

    public boolean isCompleted() {
        return status == AssessmentStatus.COMPLETED;
    }

    public UUID getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public AssessmentStatus getStatus() {
        return status;
    }

    public int getFollowUpRounds() {
        return followUpRounds;
    }

    public String getInput() {
        return input;
    }

    public String getFollowUpState() {
        return followUpState;
    }

    public String getResult() {
        return result;
    }

    public long getVersion() {
        return version;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public StoredReport getReport() {
        return report;
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }
}
