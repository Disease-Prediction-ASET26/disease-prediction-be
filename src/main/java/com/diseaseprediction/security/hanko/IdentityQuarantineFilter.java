package com.diseaseprediction.security.hanko;

import com.diseaseprediction.security.ProblemResponseWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Blocks known unsafe identities after JWT validation, before any protected API operation. */
public final class IdentityQuarantineFilter extends OncePerRequestFilter {
    private static final long MAX_BYTES = 128 * 1024;
    private final boolean enabled;
    private final Set<UUID> subjects;

    public IdentityQuarantineFilter(IdentityQuarantineProperties properties) {
        enabled = properties.enabled();
        subjects = enabled ? readSubjects(properties.file()) : Set.of();
    }

    private static Set<UUID> readSubjects(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalStateException(
                    "Identity quarantine is enabled but its file is missing");
        }
        try {
            Path file = Path.of(name);
            if (Files.size(file) > MAX_BYTES) {
                throw new IllegalStateException("Identity quarantine file is too large");
            }
            Set<UUID> values = new HashSet<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                UUID value = UUID.fromString(trimmed);
                if (!value.toString().equals(trimmed.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("Noncanonical UUID");
                }
                values.add(value);
            }
            if (values.isEmpty()) {
                throw new IllegalStateException("Identity quarantine file must not be empty");
            }
            return Set.copyOf(values);
        } catch (IOException | IllegalArgumentException exception) {
            // The file contains account identifiers; never include file contents in logs.
            throw new IllegalStateException("Identity quarantine file cannot be loaded");
        }
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!enabled || !(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            chain.doFilter(request, response);
            return;
        }
        String subject = jwtAuthentication.getToken().getSubject();
        UUID id;
        try {
            id = UUID.fromString(subject);
            if (!id.toString().equals(subject.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Noncanonical UUID");
            }
        } catch (IllegalArgumentException | NullPointerException exception) {
            deny(response);
            return;
        }
        if (subjects.contains(id)) {
            deny(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private void deny(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        ProblemResponseWriter.write(
                response,
                HttpStatus.FORBIDDEN,
                "account-quarantined",
                "This account requires review before it can access the application.");
    }
}
