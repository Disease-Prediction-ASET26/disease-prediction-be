package com.diseaseprediction.security.hanko;

/**
 * Hanko could not confirm a session. The message never contains the session token, so it is safe to
 * log.
 */
public class IdentityProviderUnavailableException extends RuntimeException {

    public IdentityProviderUnavailableException(String message) {
        super(message);
    }

    public IdentityProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
