package com.diseaseprediction.domain;

import com.diseaseprediction.domain.enums.UserRole;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * A user of the system (MDD Iteration 5). Mapped with JOINED inheritance: shared fields live in
 * {@code users}, each subclass has its own table sharing this id. Passwords and login are handled
 * by the identity provider (Keycloak); {@code identityId} links this row to that account.
 */
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "role", discriminatorType = DiscriminatorType.STRING, length = 20)
public abstract class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "identity_id", nullable = false, unique = true)
    private String identityId;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    /**
     * Read-only view of the discriminator: the subclass decides the role, code cannot change it.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, insertable = false, updatable = false, length = 20)
    private UserRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** For JPA only. */
    protected User() {}

    protected User(UserRole role, String identityId, String email, String fullName) {
        this.role = role;
        this.identityId = identityId;
        this.email = email;
        this.fullName = fullName;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public String getIdentityId() {
        return identityId;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public UserRole getRole() {
        return role;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
