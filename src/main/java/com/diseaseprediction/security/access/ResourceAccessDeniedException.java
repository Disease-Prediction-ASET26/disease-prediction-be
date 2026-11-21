package com.diseaseprediction.security.access;

import com.diseaseprediction.domain.enums.ResourceType;
import com.diseaseprediction.exception.ApiProblemException;

import org.springframework.http.HttpStatus;

import java.util.Locale;

/**
 * Refusal by {@link OwnershipAspect}. The client always gets the same 404 {@code
 * <resource>-not-found}, whether the record is unknown or belongs to someone else; the audit trail
 * records the real reason ({@link #auditReason()}) as a denial.
 */
public class ResourceAccessDeniedException extends ApiProblemException {

    /** The record exists but belongs to another user. */
    public static final String NOT_OWNER = "not-owner";

    /** No record has this id, or the call named none. */
    public static final String UNKNOWN_RESOURCE = "unknown-resource";

    private final String auditReason;

    public ResourceAccessDeniedException(ResourceType resource, String auditReason) {
        super(HttpStatus.NOT_FOUND, codeFor(resource), "No such record exists for this account.");
        this.auditReason = auditReason;
    }

    /** {@code ASSESSMENT} becomes {@code assessment-not-found}. */
    static String codeFor(ResourceType resource) {
        return resource.name().toLowerCase(Locale.ROOT).replace('_', '-') + "-not-found";
    }

    public String auditReason() {
        return auditReason;
    }
}
