package com.diseaseprediction.security.access;

import com.diseaseprediction.domain.enums.ResourceType;

import java.util.Optional;
import java.util.UUID;

/**
 * Finds who owns a record of one resource type (Strategy). {@link OwnershipAspect} picks the
 * resolver whose {@link #resource()} matches {@link OwnedResource#resource()}.
 */
public interface OwnershipResolver {

    ResourceType resource();

    /** The app user id of the owner, or empty when no such record exists. */
    Optional<UUID> ownerOf(UUID resourceId);
}
