package com.diseaseprediction.security.access;

import com.diseaseprediction.service.profile.ProfileService;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Enforces {@link RequiresActiveConsent}. Replaces the direct {@code
 * profileService.requireConsentingPatient()} calls that each assessment method made in Iteration 6,
 * so the rule is declared once per method and cannot be forgotten in the method body.
 *
 * <p>The check itself stays in {@link ProfileService#requireConsentingPatient()}, so the problem
 * codes are unchanged: {@code email-not-verified}, {@code profile-required}, {@code forbidden} (not
 * a patient) and {@code consent-required}, all 403.
 */
@Aspect
@Component
@Order(AccessGuardOrder.CONSENT)
public class ConsentAspect {

    private final ProfileService profileService;

    public ConsentAspect(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Before("@annotation(com.diseaseprediction.security.access.RequiresActiveConsent)")
    public void requireActiveConsent() {
        profileService.requireConsentingPatient();
    }
}
