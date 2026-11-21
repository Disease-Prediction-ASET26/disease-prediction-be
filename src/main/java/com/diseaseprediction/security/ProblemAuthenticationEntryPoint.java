package com.diseaseprediction.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * 401 for a missing, invalid, expired or revoked token. Keeps Spring's {@code WWW-Authenticate:
 * Bearer ...} header and adds a problem+json body with code {@code unauthorized}.
 */
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint =
            new BearerTokenAuthenticationEntryPoint();

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        bearerEntryPoint.commence(request, response, authException);
        ProblemResponseWriter.write(
                response,
                HttpStatus.UNAUTHORIZED,
                "unauthorized",
                "Sign in again: the session token is missing, invalid or expired.");
    }
}
