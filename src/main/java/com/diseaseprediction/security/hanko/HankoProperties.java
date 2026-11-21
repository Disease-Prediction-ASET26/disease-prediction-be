package com.diseaseprediction.security.hanko;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

/**
 * Hanko settings used beside the JWT check (MDD Iteration 6). The JWT settings themselves (issuer,
 * JWKS, audience) are Spring's {@code spring.security.oauth2.resourceserver.jwt.*} properties.
 *
 * @param apiUrl Hanko public API, used for {@code POST /sessions/validate}. In Kubernetes this is
 *     the in-cluster Service URL.
 * @param sessionCheckTtl how long a session check result is reused, so a logout reaches the API
 *     within this time. Never longer than the token's own expiry.
 */
@ConfigurationProperties("app.auth.hanko")
public record HankoProperties(URI apiUrl, @DefaultValue("60s") Duration sessionCheckTtl) {

    public HankoProperties {
        if (apiUrl == null) {
            throw new IllegalArgumentException(
                    "app.auth.hanko.api-url (HANKO_API_URL) is required");
        }
        if (sessionCheckTtl.isNegative() || sessionCheckTtl.isZero()) {
            throw new IllegalArgumentException("app.auth.hanko.session-check-ttl must be positive");
        }
    }
}
