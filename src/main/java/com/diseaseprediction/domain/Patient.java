package com.diseaseprediction.domain;

import com.diseaseprediction.domain.enums.UserRole;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "patients")
@PrimaryKeyJoinColumn(name = "user_id")
@DiscriminatorValue("PATIENT")
public class Patient extends User {
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /**
     * Append-only consent history (FR-G1); the last entry is the current state. PERSIST and MERGE
     * cascade so that saving a patient also stores newly recorded consents; existing rows are never
     * changed because every Consent column is updatable = false.
     */
    @OneToMany(
            mappedBy = "patient",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @OrderBy("recordedAt ASC")
    private List<Consent> consents = new ArrayList<>();

    /** For JPA only. */
    protected Patient() {}

    public Patient(String identityId, String email, String fullName, LocalDate dateOfBirth) {
        super(UserRole.PATIENT, identityId, email, fullName);
        this.dateOfBirth = dateOfBirth;
    }

    public Consent giveConsent() {
        return record(true);
    }

    public Consent revokeConsent() {
        return record(false);
    }

    public boolean hasActiveConsent() {
        return !consents.isEmpty() && consents.getLast().isGranted();
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public List<Consent> getConsents() {
        return Collections.unmodifiableList(consents);
    }

    private Consent record(boolean granted) {
        Consent previous = consents.isEmpty() ? null : consents.getLast();
        Consent consent = new Consent(this, granted, previous);
        consents.add(consent);
        return consent;
    }

    public Assessment startAssessment() {
        return null;
    }

    public void forwardReport(Report report, Doctor doctor) {}

    public File exportData() {
        return null;
    }

    public void deleteAccount() {}
}
