package com.diseaseprediction.repository;

import com.diseaseprediction.domain.Consent;
import com.diseaseprediction.domain.Patient;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConsentRepository extends JpaRepository<Consent, UUID> {
    /** The patient's current consent: the most recent grant or revoke. */
    Optional<Consent> findFirstByPatientOrderByRecordedAtDesc(Patient patient);
}
