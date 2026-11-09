package com.diseaseprediction.domain;

import com.diseaseprediction.domain.enums.UserRole;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "administrators")
@PrimaryKeyJoinColumn(name = "user_id")
@DiscriminatorValue("ADMIN")
public class Administrator extends User {
    /** For JPA only. */
    protected Administrator() {}

    public Administrator(String identityId, String email, String fullName) {
        super(UserRole.ADMIN, identityId, email, fullName);
    }

    public void verifyDoctor(Doctor doctor) {
        doctor.markVerified();
    }

    public void manageUser(User user) {}

    public void deployModel(MLModel model) {}

    public void manageRedFlagRule(RedFlagRule rule) {}

    public void processDeletionRequest(Patient patient) {}
}
