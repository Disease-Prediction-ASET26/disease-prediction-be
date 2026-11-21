package com.diseaseprediction.security.access;

import com.diseaseprediction.domain.AccessAuditEvent;
import com.diseaseprediction.domain.User;
import com.diseaseprediction.domain.enums.AccessAction;
import com.diseaseprediction.domain.enums.AccessOutcome;
import com.diseaseprediction.domain.enums.ResourceType;
import com.diseaseprediction.repository.AccessAuditEventRepository;
import com.diseaseprediction.security.CurrentUserService;

import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Writes {@link AccessAuditEvent}s. Each event is saved in its own transaction ({@code
 * REQUIRES_NEW}), so it is kept even when the caller's transaction rolls back, which is exactly
 * when a denial matters most.
 */
@Service
public class AuditTrail {

    private final AccessAuditEventRepository repository;
    private final CurrentUserService currentUserService;

    public AuditTrail(
            AccessAuditEventRepository repository, CurrentUserService currentUserService) {
        this.repository = repository;
        this.currentUserService = currentUserService;
    }

    /**
     * Records one access by the signed-in user. The actor is looked up after the call, so a call
     * that creates the profile is attributed to the new user. Callers without a profile, or without
     * a session, are recorded with no actor.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AccessAuditEvent record(
            AccessAction action,
            ResourceType resource,
            UUID resourceId,
            AccessOutcome outcome,
            String reasonCode) {
        Optional<User> actor = currentActor();
        return repository.save(
                new AccessAuditEvent(
                        actor.map(User::getId).orElse(null),
                        actor.map(User::getRole).orElse(null),
                        action,
                        resource,
                        resourceId,
                        outcome,
                        reasonCode));
    }

    private Optional<User> currentActor() {
        try {
            return currentUserService.currentUser();
        } catch (AuthenticationException notSignedIn) {
            return Optional.empty();
        }
    }
}
