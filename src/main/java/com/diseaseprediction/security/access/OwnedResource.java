package com.diseaseprediction.security.access;

import com.diseaseprediction.domain.enums.ResourceType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Only the owner of the record named by {@link #id()} may call the annotated method (NFR-1).
 * Enforced by {@link OwnershipAspect} before the method body runs. Unknown ids and other users' ids
 * are refused the same way, with a 404, so the response does not reveal whether a record exists.
 *
 * <p>Put it on Spring bean methods that are called from outside the bean: Spring AOP does not see
 * calls from the same class.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OwnedResource {

    ResourceType resource();

    /** SpEL over the method arguments that yields the record id, e.g. {@code #assessmentId}. */
    String id();

    /**
     * When {@code true}, a call whose id is missing ({@code null}) is not checked: it does not name
     * an existing record. When {@code false}, a missing id is refused like an unknown one.
     */
    boolean optional() default false;
}
