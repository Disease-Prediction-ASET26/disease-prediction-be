package com.diseaseprediction.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.diseaseprediction.TestcontainersConfiguration;
import com.diseaseprediction.domain.Administrator;
import com.diseaseprediction.domain.Consent;
import com.diseaseprediction.domain.Doctor;
import com.diseaseprediction.domain.Patient;
import com.diseaseprediction.domain.User;
import com.diseaseprediction.domain.enums.UserRole;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Validates MDD Iteration 5 against a real PostgreSQL: the Flyway V1 schema, JOINED inheritance
 * with the role discriminator, unique keys, cascade deletion and the consent history. Hibernate
 * runs with ddl-auto=validate, so the context only starts if the entities match the migration.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class UserPersistenceTest {

    @Autowired private UserRepository userRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private ConsentRepository consentRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void patientIsStoredInUsersAndPatientsWithRolePatient() {
        Patient patient =
                patientRepository.saveAndFlush(
                        new Patient(
                                "kc-patient-1",
                                "ana@example.com",
                                "Ana Pop",
                                LocalDate.of(1990, 5, 1)));

        assertThat(roleInUsersTable(patient.getId())).isEqualTo("PATIENT");
        assertThat(countRows("patients", "user_id", patient.getId())).isEqualTo(1);
        assertThat(countRows("doctors", "user_id", patient.getId())).isZero();
    }

    @Test
    void doctorAndAdministratorGetTheirOwnTypeTables() {
        Doctor doctor =
                doctorRepository.saveAndFlush(
                        new Doctor(
                                "kc-doctor-1",
                                "dr@example.com",
                                "Dr. Ion",
                                "Cardiology",
                                "LIC-001"));
        Administrator admin =
                userRepository.saveAndFlush(
                        new Administrator("kc-admin-1", "admin@example.com", "Admin"));

        assertThat(roleInUsersTable(doctor.getId())).isEqualTo("DOCTOR");
        assertThat(countRows("doctors", "user_id", doctor.getId())).isEqualTo(1);
        assertThat(roleInUsersTable(admin.getId())).isEqualTo("ADMIN");
        assertThat(countRows("administrators", "user_id", admin.getId())).isEqualTo(1);
    }

    @Test
    void findByIdentityIdReturnsTheConcreteTypeWithItsRole() {
        patientRepository.saveAndFlush(
                new Patient("kc-patient-2", "maria@example.com", "Maria", null));
        entityManager.clear();

        User found = userRepository.findByIdentityId("kc-patient-2").orElseThrow();

        assertThat(found).isInstanceOf(Patient.class);
        assertThat(found.getRole()).isEqualTo(UserRole.PATIENT);
    }

    @Test
    void duplicateIdentityIdIsRejected() {
        patientRepository.saveAndFlush(new Patient("kc-same", "a@example.com", "A", null));

        assertThatThrownBy(
                        () ->
                                patientRepository.saveAndFlush(
                                        new Patient("kc-same", "b@example.com", "B", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateLicenseNumberIsRejected() {
        doctorRepository.saveAndFlush(
                new Doctor("kc-doc-a", "a@example.com", "Dr. A", "Neurology", "LIC-DUP"));

        assertThatThrownBy(
                        () ->
                                doctorRepository.saveAndFlush(
                                        new Doctor(
                                                "kc-doc-b",
                                                "b@example.com",
                                                "Dr. B",
                                                "Neurology",
                                                "LIC-DUP")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void grantThenRevokeKeepsBothRowsAndCurrentConsentIsRevoked() {
        Patient patient =
                patientRepository.save(new Patient("kc-consent", "c@example.com", "C", null));
        patient.giveConsent();
        patient.revokeConsent();
        patientRepository.saveAndFlush(patient);
        entityManager.clear();

        Patient reloaded = patientRepository.findById(patient.getId()).orElseThrow();
        Consent current =
                consentRepository.findFirstByPatientOrderByRecordedAtDesc(reloaded).orElseThrow();

        assertThat(countRows("consents", "patient_id", patient.getId())).isEqualTo(2);
        assertThat(reloaded.getConsents()).hasSize(2);
        assertThat(reloaded.hasActiveConsent()).isFalse();
        assertThat(current.isGranted()).isFalse();
    }

    @Test
    void deletingTheUserRowCascadesToProfileAndConsents() {
        Patient patient =
                patientRepository.save(new Patient("kc-delete", "d@example.com", "D", null));
        patient.giveConsent();
        patientRepository.saveAndFlush(patient);
        UUID id = patient.getId();
        entityManager.clear();

        // Delete in SQL to prove the cascade is enforced by the database (FR-G2), not by JPA.
        entityManager
                .createNativeQuery("DELETE FROM users WHERE id = :id")
                .setParameter("id", id)
                .executeUpdate();

        assertThat(countRows("users", "id", id)).isZero();
        assertThat(countRows("patients", "user_id", id)).isZero();
        assertThat(countRows("consents", "patient_id", id)).isZero();
    }

    private String roleInUsersTable(UUID id) {
        return (String)
                entityManager
                        .createNativeQuery("SELECT role FROM users WHERE id = :id")
                        .setParameter("id", id)
                        .getSingleResult();
    }

    private long countRows(String table, String idColumn, UUID id) {
        Number count =
                (Number)
                        entityManager
                                .createNativeQuery(
                                        "SELECT count(*) FROM "
                                                + table
                                                + " WHERE "
                                                + idColumn
                                                + " = :id")
                                .setParameter("id", id)
                                .getSingleResult();
        return count.longValue();
    }
}
