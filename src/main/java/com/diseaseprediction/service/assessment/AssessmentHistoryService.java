package com.diseaseprediction.service.assessment;

import com.diseaseprediction.domain.StoredAssessment;
import com.diseaseprediction.exception.ApiProblemException;
import com.diseaseprediction.repository.PatientRepository;
import com.diseaseprediction.repository.StoredAssessmentRepository;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Stores and loads assessments (MDD Iteration 7). Each method is one short transaction; callers
 * talk to the prediction service outside of it, so no database transaction waits on a remote call.
 * Ownership is checked before these methods run ({@code @OwnedResource}).
 */
@Service
public class AssessmentHistoryService {

    private final StoredAssessmentRepository assessments;
    private final PatientRepository patients;

    public AssessmentHistoryService(
            StoredAssessmentRepository assessments, PatientRepository patients) {
        this.assessments = assessments;
        this.patients = patients;
    }

    /** Stores a new assessment of the patient with its first result and a draft report. */
    @Transactional
    public StoredAssessment create(
            UUID patientId,
            String input,
            String followUpState,
            int followUpRounds,
            String result,
            boolean completed) {
        StoredAssessment assessment =
                new StoredAssessment(
                        patients.getReferenceById(patientId),
                        input,
                        followUpState,
                        followUpRounds,
                        result,
                        completed);
        return assessments.saveAndFlush(assessment);
    }

    /**
     * @throws ApiProblemException 404 {@code assessment-not-found} if it no longer exists
     */
    @Transactional(readOnly = true)
    public StoredAssessment load(UUID assessmentId) {
        return assessments
                .findById(assessmentId)
                .orElseThrow(ApiProblemException::assessmentNotFound);
    }

    /**
     * Saves one answered follow-up question, if nobody saved another answer since {@code
     * expectedVersion} was read.
     *
     * @throws ApiProblemException 409 {@code assessment-conflict} if another answer was saved
     *     first, 409 {@code assessment-completed} if the assessment is already completed
     */
    @Transactional
    public StoredAssessment recordFollowUp(
            UUID assessmentId,
            long expectedVersion,
            String followUpState,
            String result,
            boolean completed) {
        StoredAssessment assessment = load(assessmentId);
        if (assessment.getVersion() != expectedVersion) {
            throw ApiProblemException.assessmentConflict();
        }
        if (assessment.isCompleted()) {
            throw ApiProblemException.assessmentCompleted();
        }
        assessment.recordFollowUp(followUpState, result, completed);
        try {
            return assessments.saveAndFlush(assessment);
        } catch (ObjectOptimisticLockingFailureException concurrentAnswer) {
            throw ApiProblemException.assessmentConflict();
        }
    }

    /** The patient's assessments, newest first. */
    @Transactional(readOnly = true)
    public List<StoredAssessment> listFor(UUID patientId) {
        return assessments.findByPatientIdOrderByCreatedAtDesc(patientId);
    }
}
