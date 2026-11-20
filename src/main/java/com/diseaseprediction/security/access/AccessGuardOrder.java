package com.diseaseprediction.security.access;

/**
 * The fixed order of the Personal Data Access Guard aspects, outermost first. All of them run
 * before Spring's transaction interceptor (lowest precedence), so they wrap the business
 * transaction rather than run inside it.
 */
public final class AccessGuardOrder {

    /** {@link AccessAuditAspect}: outermost, so it sees the denials of the inner guards. */
    public static final int AUDIT = 1;

    /** {@link ConsentAspect}. */
    public static final int CONSENT = 2;

    private AccessGuardOrder() {}
}
