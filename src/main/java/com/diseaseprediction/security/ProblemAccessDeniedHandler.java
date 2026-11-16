package com.diseaseprediction.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * 403 for a valid session without the required role. A signed-in user with no app profile yet has
 * no role at all, so the response tells the client to finish onboarding ({@code profile-required});
 * otherwise the code is {@code forbidden}.
 */
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean hasRole =
                authentication != null
                        && authentication.getAuthorities().stream()
                                .anyMatch(
                                        authority ->
                                                authority.getAuthority() != null
                                                        && authority
                                                                .getAuthority()
                                                                .startsWith(
                                                                        RoleAuthoritiesConverter
                                                                                .ROLE_PREFIX));
        if (hasRole) {
            ProblemResponseWriter.write(
                    response,
                    HttpStatus.FORBIDDEN,
                    "forbidden",
                    "Your account is not allowed to do this.");
        } else {
            ProblemResponseWriter.write(
                    response,
                    HttpStatus.FORBIDDEN,
                    "profile-required",
                    "Complete your profile before using this feature.");
        }
    }
}
