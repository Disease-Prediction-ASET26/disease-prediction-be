package com.diseaseprediction.security.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Only a signed-in patient with a verified email and an active consent to health-data processing
 * may call the annotated method (UC1 step 1, FR-G1). Enforced by {@link ConsentAspect} before the
 * method body runs; a refused call is answered with 403 and never reaches the method.
 *
 * <p>Put it on Spring bean methods that are called from outside the bean: Spring AOP does not see
 * calls from the same class.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresActiveConsent {}
