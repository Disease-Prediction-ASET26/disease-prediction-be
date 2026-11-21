package com.diseaseprediction.security.hanko;

/** Asks Hanko whether a session is still active (not logged out or revoked). */
public interface HankoSessionClient {

    /**
     * @param sessionToken the Hanko session JWT, already verified offline
     * @return {@code true} if Hanko reports the session as valid
     * @throws IdentityProviderUnavailableException if Hanko cannot be reached or answers with an
     *     error. Callers must deny the request in that case.
     */
    boolean isSessionValid(String sessionToken);
}
