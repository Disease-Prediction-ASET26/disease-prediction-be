package com.diseaseprediction.security.hanko;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Optional denylist of Hanko subjects that must not access the application API. */
@ConfigurationProperties("app.auth.hanko.quarantine")
public record IdentityQuarantineProperties(@DefaultValue("false") boolean enabled, String file) {}
