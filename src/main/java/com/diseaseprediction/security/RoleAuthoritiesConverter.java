package com.diseaseprediction.security;

import com.diseaseprediction.repository.UserRepository;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

/**
 * Turns a verified Hanko JWT into an authentication with the user's role from the app database (MDD
 * Iteration 6, decision 4). Roles are never read from the token or the request. A user who has not
 * finished onboarding has no profile and therefore no role authority.
 *
 * <p>Not a Spring bean on purpose: Spring MVC registers every {@code Converter} bean as a web type
 * converter. {@link SecurityConfig} creates it.
 */
public class RoleAuthoritiesConverter implements Converter<Jwt, JwtAuthenticationToken> {

    public static final String ROLE_PREFIX = "ROLE_";

    private final UserRepository userRepository;

    public RoleAuthoritiesConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public JwtAuthenticationToken convert(Jwt jwt) {
        String subject = jwt.getSubject();
        List<GrantedAuthority> authorities =
                subject == null
                        ? List.of()
                        : userRepository
                                .findByIdentityId(subject)
                                .<List<GrantedAuthority>>map(
                                        user ->
                                                List.of(
                                                        new SimpleGrantedAuthority(
                                                                ROLE_PREFIX
                                                                        + user.getRole().name())))
                                .orElse(List.of());
        return new JwtAuthenticationToken(jwt, authorities, subject);
    }
}
