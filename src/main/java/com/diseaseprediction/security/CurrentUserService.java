package com.diseaseprediction.security;

import com.diseaseprediction.domain.User;
import com.diseaseprediction.repository.UserRepository;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * The signed-in user for the current request. The identity always comes from the verified token,
 * never from request parameters or bodies.
 */
@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * @throws AuthenticationCredentialsNotFoundException if the request is not authenticated with a
     *     JWT (answered with 401)
     */
    public AuthenticatedIdentity identity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return AuthenticatedIdentity.from(jwtAuthentication.getToken());
        }
        throw new AuthenticationCredentialsNotFoundException("No signed-in user");
    }

    /** The app user linked to the signed-in identity, or empty before onboarding. */
    public Optional<User> currentUser() {
        return userRepository.findByIdentityId(identity().subject());
    }
}
