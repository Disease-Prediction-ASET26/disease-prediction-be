package com.diseaseprediction.security.access;

import com.diseaseprediction.domain.enums.AccessOutcome;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Enforces {@link AuditedAccess}: records {@code ALLOWED} when the method returns, {@code DENIED}
 * when the caller is refused (a 401/403, including refusals by the inner guards such as {@link
 * ConsentAspect}) and {@code FAILED} for any other exception, which is then rethrown unchanged.
 *
 * <p>Auditing never changes the result of the call: if the audit row cannot be written, the error
 * is logged and the caller gets the response the method produced. The guarded method has already
 * committed its own transaction by then, so failing the request would misreport what happened.
 */
@Aspect
@Component
@Order(AccessGuardOrder.AUDIT)
public class AccessAuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AccessAuditAspect.class);

    private final AuditTrail auditTrail;

    public AccessAuditAspect(AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint call, AuditedAccess audited) throws Throwable {
        Object result;
        try {
            result = call.proceed();
        } catch (Throwable failure) {
            record(audited, AccessVerdict.of(failure));
            throw failure;
        }
        record(audited, AccessVerdict.ALLOWED);
        return result;
    }

    private void record(AuditedAccess audited, AccessVerdict verdict) {
        try {
            auditTrail.record(
                    audited.action(), audited.resource(), verdict.outcome(), verdict.reasonCode());
        } catch (RuntimeException auditFailure) {
            log.error(
                    "Could not record access audit event {} {} {}",
                    audited.action(),
                    audited.resource(),
                    verdict.outcome(),
                    auditFailure);
        }
        if (verdict.outcome() == AccessOutcome.DENIED) {
            log.info(
                    "Access denied: {} {} ({})",
                    audited.action(),
                    audited.resource(),
                    verdict.reasonCode());
        }
    }
}
