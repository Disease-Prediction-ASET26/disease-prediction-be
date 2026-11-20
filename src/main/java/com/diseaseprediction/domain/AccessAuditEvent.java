package com.diseaseprediction.domain;

import com.diseaseprediction.domain.enums.AccessAction;
import com.diseaseprediction.domain.enums.AccessOutcome;
import com.diseaseprediction.domain.enums.ResourceType;
import com.diseaseprediction.domain.enums.UserRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * One access to personal data, recorded by the Personal Data Access Guard (Lab 9, Part B). Holds
 * ids, codes and the actor's role only, never tokens, names, emails or symptoms. The table is
 * append-only: a database trigger rejects {@code UPDATE}, {@code DELETE} and {@code TRUNCATE}.
 */
@Entity
@Immutable
@Table(name = "access_audit_events")
public class AccessAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    /** The app user, or {@code null} when the caller has no profile yet or is not signed in. */
    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", updatable = false, length = 20)
    private UserRole actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private AccessAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, updatable = false, length = 40)
    private ResourceType resourceType;

    /** The record that was accessed, when the call names one. */
    @Column(name = "resource_id", updatable = false)
    private UUID resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private AccessOutcome outcome;

    /** A problem code such as {@code consent-required}, or {@code null} when allowed. */
    @Column(name = "reason_code", updatable = false, length = 64)
    private String reasonCode;

    /** For JPA only. */
    protected AccessAuditEvent() {}

    public AccessAuditEvent(
            UUID actorUserId,
            UserRole actorRole,
            AccessAction action,
            ResourceType resourceType,
            UUID resourceId,
            AccessOutcome outcome,
            String reasonCode) {
        this.occurredAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        this.actorUserId = actorUserId;
        this.actorRole = actorRole;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.outcome = outcome;
        this.reasonCode = reasonCode;
    }

    public UUID getId() {
        return id;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public UserRole getActorRole() {
        return actorRole;
    }

    public AccessAction getAction() {
        return action;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public AccessOutcome getOutcome() {
        return outcome;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
