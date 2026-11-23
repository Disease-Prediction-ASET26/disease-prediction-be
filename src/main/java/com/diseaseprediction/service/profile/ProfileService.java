package com.diseaseprediction.service.profile;

import com.diseaseprediction.api.model.ConsentRequest;
import com.diseaseprediction.api.model.CreateProfileRequest;
import com.diseaseprediction.api.model.Profile;
import com.diseaseprediction.api.model.Role;
import com.diseaseprediction.domain.Patient;
import com.diseaseprediction.domain.User;
import com.diseaseprediction.domain.enums.AccessAction;
import com.diseaseprediction.domain.enums.ResourceType;
import com.diseaseprediction.exception.ApiProblemException;
import com.diseaseprediction.repository.PatientRepository;
import com.diseaseprediction.repository.UserRepository;
import com.diseaseprediction.security.AuthenticatedIdentity;
import com.diseaseprediction.security.CurrentUserService;
import com.diseaseprediction.security.access.AuditedAccess;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * The signed-in user's profile and consent (MDD Iteration 6). Hanko proves who the user is; this
 * service decides what they are in the app. The identity and email always come from the verified
 * session token.
 */
@Service
public class ProfileService {

    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final PatientRepository patientRepository;

    public ProfileService(
            CurrentUserService currentUserService,
            UserRepository userRepository,
            PatientRepository patientRepository) {
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
    }

    /**
     * The profile of the signed-in user. Also refreshes the stored email when Hanko reports a
     * different verified address.
     *
     * @throws ApiProblemException 404 {@code profile-not-found} on the first login
     */
    @AuditedAccess(action = AccessAction.READ, resource = ResourceType.PATIENT_PROFILE)
    @Transactional
    public Profile getProfile() {
        AuthenticatedIdentity identity = requireVerifiedEmail(HttpStatus.FORBIDDEN);
        User user =
                userRepository
                        .findByIdentityId(identity.subject())
                        .orElseThrow(ApiProblemException::profileNotFound);
        if (identity.emailVerified()
                && identity.email() != null
                && !identity.email().equals(user.getEmail())) {
            user.changeEmail(identity.email());
        }
        return toProfile(user);
    }

    /**
     * First-login onboarding: creates a patient profile with its first consent. New accounts are
     * always patients.
     */
    @AuditedAccess(action = AccessAction.CREATE, resource = ResourceType.PATIENT_PROFILE)
    @Transactional
    public Profile createPatientProfile(CreateProfileRequest request) {
        AuthenticatedIdentity identity = requireVerifiedEmail(HttpStatus.BAD_REQUEST);
        if (userRepository.findByIdentityId(identity.subject()).isPresent()) {
            throw ApiProblemException.profileExists();
        }
        if (!Boolean.TRUE.equals(request.getConsentGranted())) {
            throw ApiProblemException.consentRequired(HttpStatus.BAD_REQUEST);
        }
        String fullName = request.getFullName() == null ? "" : request.getFullName().strip();
        if (fullName.isEmpty()) {
            throw ApiProblemException.invalidProfile("Full name must not be blank.");
        }
        LocalDate dateOfBirth = request.getDateOfBirth();
        if (dateOfBirth != null && !dateOfBirth.isBefore(LocalDate.now(ZoneOffset.UTC))) {
            throw ApiProblemException.invalidProfile("Date of birth must be in the past.");
        }

        Patient patient = new Patient(identity.subject(), identity.email(), fullName, dateOfBirth);
        patient.giveConsent();
        try {
            patientRepository.saveAndFlush(patient);
        } catch (DataIntegrityViolationException concurrentOnboarding) {
            // Two onboarding requests for the same account: identity_id is unique.
            throw ApiProblemException.profileExists();
        }
        return toProfile(patient);
    }

    /** Appends a grant or revoke to the signed-in patient's consent history (FR-G1). */
    @AuditedAccess(action = AccessAction.UPDATE, resource = ResourceType.CONSENT)
    @Transactional
    public Profile recordConsent(ConsentRequest request) {
        Patient patient = currentPatient();
        if (Boolean.TRUE.equals(request.getGranted())) {
            patient.giveConsent();
        } else {
            patient.revokeConsent();
        }
        patientRepository.save(patient);
        return toProfile(patient);
    }

    /**
     * Guard for features that process health data (UC1 step 1). Called by {@link
     * com.diseaseprediction.security.access.ConsentAspect} for methods annotated with {@link
     * com.diseaseprediction.security.access.RequiresActiveConsent}; do not call it directly.
     *
     * @return the signed-in patient
     * @throws ApiProblemException 403 {@code profile-required}, {@code forbidden} or {@code
     *     consent-required}
     */
    @Transactional(readOnly = true)
    public Patient requireConsentingPatient() {
        Patient patient = currentPatient();
        if (!patient.hasActiveConsent()) {
            throw ApiProblemException.consentRequired(HttpStatus.FORBIDDEN);
        }
        return patient;
    }

    /**
     * Guard for reading the patient's own health data (MDD Iteration 7). Needs a verified email but
     * no active consent: withdrawing consent stops new processing, not access to one's data.
     *
     * @return the signed-in patient
     * @throws ApiProblemException 403 {@code email-not-verified}, {@code profile-required} or
     *     {@code forbidden}
     */
    @Transactional(readOnly = true)
    public Patient requirePatient() {
        return currentPatient();
    }

    private Patient currentPatient() {
        requireVerifiedEmail(HttpStatus.FORBIDDEN);
        User user =
                currentUserService.currentUser().orElseThrow(ApiProblemException::profileRequired);
        if (user instanceof Patient patient) {
            return patient;
        }
        throw ApiProblemException.forbidden();
    }

    private AuthenticatedIdentity requireVerifiedEmail(HttpStatus status) {
        AuthenticatedIdentity identity = currentUserService.identity();
        if (identity.email() == null || identity.email().isBlank() || !identity.emailVerified()) {
            throw ApiProblemException.emailNotVerified(status);
        }
        return identity;
    }

    private static Profile toProfile(User user) {
        Profile profile =
                new Profile()
                        .id(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .role(Role.fromValue(user.getRole().name()))
                        .consentGranted(false);
        if (user instanceof Patient patient) {
            profile.dateOfBirth(patient.getDateOfBirth())
                    .consentGranted(patient.hasActiveConsent());
        }
        return profile;
    }
}
