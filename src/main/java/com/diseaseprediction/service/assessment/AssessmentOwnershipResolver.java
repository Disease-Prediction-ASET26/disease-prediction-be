package com.diseaseprediction.service.assessment;

import com.diseaseprediction.domain.enums.ResourceType;
import com.diseaseprediction.repository.StoredAssessmentRepository;
import com.diseaseprediction.security.access.OwnershipResolver;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Who owns a stored assessment: its patient. Reads the owner id only, never the health data. */
@Component
public class AssessmentOwnershipResolver implements OwnershipResolver {

    private final StoredAssessmentRepository assessments;

    public AssessmentOwnershipResolver(StoredAssessmentRepository assessments) {
        this.assessments = assessments;
    }

    @Override
    public ResourceType resource() {
        return ResourceType.ASSESSMENT;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> ownerOf(UUID assessmentId) {
        return assessments.findOwnerId(assessmentId);
    }
}
