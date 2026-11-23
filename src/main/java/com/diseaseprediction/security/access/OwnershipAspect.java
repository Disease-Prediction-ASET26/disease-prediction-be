package com.diseaseprediction.security.access;

import com.diseaseprediction.domain.User;
import com.diseaseprediction.domain.enums.ResourceType;
import com.diseaseprediction.exception.ApiProblemException;
import com.diseaseprediction.security.CurrentUserService;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Enforces {@link OwnedResource}: reads the record id from the arguments, asks the {@link
 * OwnershipResolver} for that resource type who owns it, and lets only the owner through. Runs
 * after the consent check, so a patient without consent is told about consent first.
 *
 * <p>Sharing a record with a doctor comes with the doctor portal (Iteration 12); until then only
 * the owner is allowed.
 */
@Aspect
@Component
@Order(AccessGuardOrder.OWNERSHIP)
public class OwnershipAspect {

    private final CurrentUserService currentUserService;
    private final MethodExpressions expressions;
    private final Map<ResourceType, OwnershipResolver> resolvers =
            new EnumMap<>(ResourceType.class);

    public OwnershipAspect(
            CurrentUserService currentUserService,
            MethodExpressions expressions,
            List<OwnershipResolver> resolvers) {
        this.currentUserService = currentUserService;
        this.expressions = expressions;
        for (OwnershipResolver resolver : resolvers) {
            if (this.resolvers.put(resolver.resource(), resolver) != null) {
                throw new IllegalStateException(
                        "More than one OwnershipResolver for " + resolver.resource());
            }
        }
    }

    @Before("@annotation(owned)")
    public void requireOwner(JoinPoint call, OwnedResource owned) {
        UUID resourceId = readId(call, owned);
        if (resourceId == null) {
            if (owned.optional()) {
                return;
            }
            throw new ResourceAccessDeniedException(
                    owned.resource(), ResourceAccessDeniedException.UNKNOWN_RESOURCE);
        }
        User caller =
                currentUserService.currentUser().orElseThrow(ApiProblemException::profileRequired);
        OwnershipResolver resolver = resolvers.get(owned.resource());
        if (resolver == null) {
            throw new IllegalStateException("No OwnershipResolver for " + owned.resource());
        }
        UUID owner =
                resolver.ownerOf(resourceId)
                        .orElseThrow(
                                () ->
                                        new ResourceAccessDeniedException(
                                                owned.resource(),
                                                ResourceAccessDeniedException.UNKNOWN_RESOURCE));
        if (!owner.equals(caller.getId())) {
            throw new ResourceAccessDeniedException(
                    owned.resource(), ResourceAccessDeniedException.NOT_OWNER);
        }
    }

    /** A malformed id is treated like an unknown record. */
    private UUID readId(JoinPoint call, OwnedResource owned) {
        try {
            return expressions.resourceId(call, owned.id(), null);
        } catch (IllegalArgumentException malformed) {
            throw new ResourceAccessDeniedException(
                    owned.resource(), ResourceAccessDeniedException.UNKNOWN_RESOURCE);
        }
    }
}
