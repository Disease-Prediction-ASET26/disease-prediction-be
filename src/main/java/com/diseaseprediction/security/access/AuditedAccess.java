package com.diseaseprediction.security.access;

import com.diseaseprediction.domain.enums.AccessAction;
import com.diseaseprediction.domain.enums.ResourceType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Records every call of the annotated method in the access audit trail: who called it, what it did
 * with which kind of personal data, and whether it was allowed. Enforced by {@link
 * AccessAuditAspect}, which runs outside every other guard so their denials are recorded too.
 *
 * <p>Put it on Spring bean methods that are called from outside the bean: Spring AOP does not see
 * calls from the same class.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditedAccess {

    AccessAction action();

    ResourceType resource();
}
