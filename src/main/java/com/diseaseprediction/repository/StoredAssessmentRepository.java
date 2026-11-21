package com.diseaseprediction.repository;

import com.diseaseprediction.domain.StoredAssessment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoredAssessmentRepository extends JpaRepository<StoredAssessment, UUID> {

    /** The patient's assessments, newest first, with their reports. */
    @EntityGraph(attributePaths = "report")
    List<StoredAssessment> findByPatientIdOrderByCreatedAtDesc(UUID patientId);

    /** The owner of an assessment, without loading its encrypted health data. */
    @Query("SELECT a.patient.id FROM StoredAssessment a WHERE a.id = :id")
    Optional<UUID> findOwnerId(@Param("id") UUID id);
}
