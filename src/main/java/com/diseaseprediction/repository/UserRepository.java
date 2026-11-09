package com.diseaseprediction.repository;

import com.diseaseprediction.domain.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    /** Finds the user linked to an identity-provider account (the token's "sub" claim). */
    Optional<User> findByIdentityId(String identityId);
}
