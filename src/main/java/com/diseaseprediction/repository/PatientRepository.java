package com.diseaseprediction.repository;

import com.diseaseprediction.domain.Patient;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {}
