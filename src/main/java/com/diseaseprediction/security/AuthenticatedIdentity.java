package com.diseaseprediction.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;

/**
 * Who sent the request, as proven by the verified Hanko session token.
 *
 * @param subject the Hanko user id ({@code sub}), stored as {@code users.identity_id}
 * @param sessionId the Hanko session id ({@code session_id})
 * @param email the primary email address from the {@code email} claim, or {@code null}
 * @param emailVerified whether Hanko has verified that address
 */
public record AuthenticatedIdentity(
        String subject, String sessionId, String email, boolean emailVerified) {

    /** Reads the claims Hanko v3 puts in every session token. */
    public static AuthenticatedIdentity from(Jwt jwt) {
        String email = null;
        boolean verified = false;
        if (jwt.getClaims().get("email") instanceof Map<?, ?> emailClaim) {
            if (emailClaim.get("address") instanceof String address && !address.isBlank()) {
                email = address;
            }
            verified = Boolean.TRUE.equals(emailClaim.get("is_verified"));
        }
        return new AuthenticatedIdentity(
                jwt.getSubject(), jwt.getClaimAsString("session_id"), email, verified);
    }
}
