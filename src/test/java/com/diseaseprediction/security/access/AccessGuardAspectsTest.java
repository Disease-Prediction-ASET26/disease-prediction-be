package com.diseaseprediction.security.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.diseaseprediction.domain.enums.AccessAction;
import com.diseaseprediction.domain.enums.AccessOutcome;
import com.diseaseprediction.domain.enums.ResourceType;
import com.diseaseprediction.exception.ApiProblemException;
import com.diseaseprediction.exception.InvalidAssessmentRequestException;
import com.diseaseprediction.service.profile.ProfileService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Personal Data Access Guard aspects woven by Spring AOP around a test bean, with the consent
 * check and the audit trail mocked. Covers the aspect order (audit outside consent), what each
 * outcome is recorded as, and that auditing never changes the result of a call.
 */
@SpringJUnitConfig(AccessGuardAspectsTest.Config.class)
class AccessGuardAspectsTest {

    @Configuration
    @EnableAspectJAutoProxy
    static class Config {
        @Bean
        ProfileService profileService() {
            return mock(ProfileService.class);
        }

        @Bean
        AuditTrail auditTrail() {
            return mock(AuditTrail.class);
        }

        @Bean
        AccessAuditAspect accessAuditAspect(AuditTrail auditTrail) {
            return new AccessAuditAspect(auditTrail);
        }

        @Bean
        ConsentAspect consentAspect(ProfileService profileService) {
            return new ConsentAspect(profileService);
        }

        @Bean
        GuardedTarget guardedTarget() {
            return new GuardedTarget();
        }
    }

    /** Stands in for a service that processes health data. */
    static class GuardedTarget {
        private final AtomicInteger calls = new AtomicInteger();

        /** A method, not a field: the test sees the CGLIB proxy, whose own fields are unset. */
        public int callCount() {
            return calls.get();
        }

        public void resetCalls() {
            calls.set(0);
        }

        @AuditedAccess(action = AccessAction.CREATE, resource = ResourceType.ASSESSMENT)
        @RequiresActiveConsent
        public String assess() {
            calls.incrementAndGet();
            return "result";
        }

        @AuditedAccess(action = AccessAction.UPDATE, resource = ResourceType.ASSESSMENT)
        @RequiresActiveConsent
        public String assessInvalid() {
            calls.incrementAndGet();
            throw new InvalidAssessmentRequestException("Assessment must include free text.");
        }

        @AuditedAccess(action = AccessAction.READ, resource = ResourceType.PATIENT_PROFILE)
        public String readMissingProfile() {
            calls.incrementAndGet();
            throw ApiProblemException.profileNotFound();
        }

        @AuditedAccess(action = AccessAction.READ, resource = ResourceType.PATIENT_PROFILE)
        public String readWithoutSession() {
            calls.incrementAndGet();
            throw new AuthenticationCredentialsNotFoundException("No signed-in user");
        }

        @RequiresActiveConsent
        public String consentOnly() {
            calls.incrementAndGet();
            return "consent only";
        }

        public String unguarded() {
            calls.incrementAndGet();
            return "unguarded";
        }
    }

    @Autowired private GuardedTarget target;
    @Autowired private ProfileService profileService;
    @Autowired private AuditTrail auditTrail;

    @BeforeEach
    void resetMocks() {
        reset(profileService, auditTrail);
        target.resetCalls();
    }

    @Test
    void theTargetIsProxied() {
        assertThat(AopUtils.isAopProxy(target)).isTrue();
    }

    @Test
    void anAllowedCallChecksConsentFirstAndIsRecordedAfterwards() {
        assertThat(target.assess()).isEqualTo("result");

        assertThat(target.callCount()).isEqualTo(1);
        InOrder order = inOrder(profileService, auditTrail);
        order.verify(profileService).requireConsentingPatient();
        order.verify(auditTrail)
                .record(AccessAction.CREATE, ResourceType.ASSESSMENT, AccessOutcome.ALLOWED, null);
    }

    @Test
    void aConsentRefusalStopsTheCallAndIsRecordedAsDenied() {
        ApiProblemException refusal = ApiProblemException.consentRequired(HttpStatus.FORBIDDEN);
        when(profileService.requireConsentingPatient()).thenThrow(refusal);

        assertThatThrownBy(target::assess).isSameAs(refusal);

        assertThat(target.callCount()).isEqualTo(0);
        verify(auditTrail)
                .record(
                        AccessAction.CREATE,
                        ResourceType.ASSESSMENT,
                        AccessOutcome.DENIED,
                        "consent-required");
    }

    @Test
    void aCallerWhoIsNotAPatientIsRecordedAsDenied() {
        when(profileService.requireConsentingPatient()).thenThrow(ApiProblemException.forbidden());

        assertThatThrownBy(target::assess).isInstanceOf(ApiProblemException.class);

        verify(auditTrail)
                .record(
                        AccessAction.CREATE,
                        ResourceType.ASSESSMENT,
                        AccessOutcome.DENIED,
                        "forbidden");
    }

    @Test
    void invalidInputAfterTheGuardsIsRecordedAsFailed() {
        assertThatThrownBy(target::assessInvalid)
                .isInstanceOf(InvalidAssessmentRequestException.class);

        assertThat(target.callCount()).isEqualTo(1);
        verify(auditTrail)
                .record(
                        AccessAction.UPDATE,
                        ResourceType.ASSESSMENT,
                        AccessOutcome.FAILED,
                        "InvalidAssessmentRequestException");
    }

    @Test
    void aProblemThatIsNotARefusalIsRecordedAsFailedWithItsCode() {
        assertThatThrownBy(target::readMissingProfile).isInstanceOf(ApiProblemException.class);

        verify(auditTrail)
                .record(
                        AccessAction.READ,
                        ResourceType.PATIENT_PROFILE,
                        AccessOutcome.FAILED,
                        "profile-not-found");
    }

    @Test
    void aMissingSessionIsRecordedAsDenied() {
        assertThatThrownBy(target::readWithoutSession)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        verify(auditTrail)
                .record(
                        AccessAction.READ,
                        ResourceType.PATIENT_PROFILE,
                        AccessOutcome.DENIED,
                        "unauthenticated");
    }

    @Test
    void anAuditFailureDoesNotChangeTheResult() {
        doThrow(new IllegalStateException("database down"))
                .when(auditTrail)
                .record(any(), any(), any(), any());

        assertThat(target.assess()).isEqualTo("result");
    }

    @Test
    void anAuditFailureDoesNotHideTheOriginalRefusal() {
        ApiProblemException refusal = ApiProblemException.consentRequired(HttpStatus.FORBIDDEN);
        when(profileService.requireConsentingPatient()).thenThrow(refusal);
        doThrow(new IllegalStateException("database down"))
                .when(auditTrail)
                .record(any(), any(), any(), any());

        assertThatThrownBy(target::assess).isSameAs(refusal);
    }

    @Test
    void consentCanBeRequiredWithoutAuditing() {
        assertThat(target.consentOnly()).isEqualTo("consent only");

        verify(profileService).requireConsentingPatient();
        verifyNoInteractions(auditTrail);
    }

    @Test
    void unannotatedMethodsAreNotGuarded() {
        assertThat(target.unguarded()).isEqualTo("unguarded");

        verify(profileService, never()).requireConsentingPatient();
        verifyNoInteractions(auditTrail);
    }
}
