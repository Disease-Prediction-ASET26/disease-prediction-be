package com.diseaseprediction.domain;

import com.diseaseprediction.domain.enums.UserRole;

import java.time.LocalDateTime;
import java.util.UUID;

public abstract class User {

    private UUID userId;
    private String email;
    private String passwordHash;
    private String fullName;
    private UserRole role;
    private LocalDateTime createdAt;

    public boolean login(String email, String password) {
        return false;
    }

    public String getEmail() {
        return email;
    }

    public void logout() {
    }
}
