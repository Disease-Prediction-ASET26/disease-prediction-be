package com.diseaseprediction.domain;

import com.diseaseprediction.domain.enums.UserRole;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

import java.util.List;

@Entity
@Table(name = "doctors")
@PrimaryKeyJoinColumn(name = "user_id")
@DiscriminatorValue("DOCTOR")
public class Doctor extends User {
    @Column(nullable = false, length = 100)
    private String specialty;

    @Column(name = "license_number", nullable = false, unique = true, length = 64)
    private String licenseNumber;

    @Column(name = "is_verified", nullable = false)
    private boolean verified = false;

    /** For JPA only. */
    protected Doctor() {}

    public Doctor(
            String identityId,
            String email,
            String fullName,
            String specialty,
            String licenseNumber) {
        super(UserRole.DOCTOR, identityId, email, fullName);
        this.specialty = specialty;
        this.licenseNumber = licenseNumber;
    }

    public String getSpecialty() {
        return specialty;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public boolean isVerified() {
        return verified;
    }

    /** Called when an administrator has checked the doctor's license. */
    void markVerified() {
        this.verified = true;
    }

    public List<Report> viewInbox() {
        return null;
    }

    public Report openReport(Report report) {
        return null;
    }

    public void addClinicalNotes(Report report, String notes) {}
}
