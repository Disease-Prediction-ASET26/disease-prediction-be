package com.diseaseprediction.security.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.diseaseprediction.domain.Patient;
import com.diseaseprediction.domain.enums.AccessAction;
import com.diseaseprediction.domain.enums.AccessOutcome;
import com.diseaseprediction.domain.enums.ResourceType;
import com.diseaseprediction.exception.ApiProblemException;
import com.diseaseprediction.security.CurrentUserService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link OwnershipAspect} woven by Spring AOP around a test bean, inside the audit aspect, with the
 * current user and the owner lookup mocked.
 */
@SpringJUnitConfig(OwnershipAspectTest.Config.class)
class OwnershipAspectTest {

    static final UUID OWNER = UUID.randomUUID();
    static final UUID RECORD = UUID.randomUUID();

    @Configuration
    @EnableAspectJAutoProxy
    static class Config {
        @Bean
        CurrentUserService currentUserService() {
            return mock(CurrentUserService.class);
        }

        @Bean
        OwnershipResolver assessmentResolver() {
            OwnershipResolver resolver = mock(OwnershipResolver.class);
            when(resolver.resource()).thenReturn(ResourceType.ASSESSMENT);
            return resolver;
        }

        @Bean
        MethodExpressions methodExpressions() {
            return new MethodExpressions();
        }

        @Bean
        AuditTrail auditTrail() {
            return mock(AuditTrail.class);
        }

        @Bean
        AccessAuditAspect accessAuditAspect(AuditTrail auditTrail, MethodExpressions expressions) {
            return new AccessAuditAspect(auditTrail, expressions);
        }

        @Bean
        OwnershipAspect ownershipAspect(
                CurrentUserService users,
                MethodExpressions expressions,
                List<OwnershipResolver> resolvers) {
            return new OwnershipAspect(users, expressions, resolvers);
        }

        @Bean
        Records records() {
            return new Records();
        }
    }

    /** A record with an id inside its argument. */
    public record Request(UUID recordId) {}

    /** Stands in for a service that reads one stored record. */
    static class Records {
        private final AtomicInteger calls = new AtomicInteger();

        public int callCount() {
            return calls.get();
        }

        public void resetCalls() {
            calls.set(0);
        }

        @AuditedAccess(
                action = AccessAction.READ,
                resource = ResourceType.ASSESSMENT,
                resourceId = "#id")
        @OwnedResource(resource = ResourceType.ASSESSMENT, id = "#id")
        public String read(UUID id) {
            calls.incrementAndGet();
            return "record";
        }

        @OwnedResource(
                resource = ResourceType.ASSESSMENT,
                id = "#request?.recordId",
                optional = true)
        public String continueOrStart(Request request) {
            calls.incrementAndGet();
            return "continued";
        }

        @OwnedResource(resource = ResourceType.ASSESSMENT, id = "#id")
        public String readByText(String id) {
            calls.incrementAndGet();
            return "record";
        }

        @OwnedResource(resource = ResourceType.ASSESSMENT, id = "#count")
        public String readByNumber(int count) {
            calls.incrementAndGet();
            return "record";
        }

        @AuditedAccess(
                action = AccessAction.READ,
                resource = ResourceType.ASSESSMENT,
                resourceId = "'not-a-uuid'")
        public String readWithBadAuditId() {
            calls.incrementAndGet();
            return "record";
        }

        @OwnedResource(resource = ResourceType.PATIENT_PROFILE, id = "#id")
        public String readProfile(UUID id) {
            calls.incrementAndGet();
            return "profile";
        }
    }

    @Autowired private Records records;
    @Autowired private CurrentUserService users;
    @Autowired private OwnershipResolver resolver;
    @Autowired private AuditTrail auditTrail;

    @BeforeEach
    void signInTheOwner() {
        reset(users, auditTrail);
        records.resetCalls();
        Patient owner = mock(Patient.class);
        when(owner.getId()).thenReturn(OWNER);
        when(users.currentUser()).thenReturn(Optional.of(owner));
        when(resolver.ownerOf(RECORD)).thenReturn(Optional.of(OWNER));
    }

    @Test
    void theOwnerGetsTheRecordAndTheAuditNamesIt() {
        assertThat(records.read(RECORD)).isEqualTo("record");

        verify(auditTrail)
                .record(
                        AccessAction.READ,
                        ResourceType.ASSESSMENT,
                        RECORD,
                        AccessOutcome.ALLOWED,
                        null);
    }

    @Test
    void anotherUsersRecordIsRefusedAsNotFoundAndAuditedAsNotOwner() {
        UUID foreign = UUID.randomUUID();
        when(resolver.ownerOf(foreign)).thenReturn(Optional.of(UUID.randomUUID()));

        assertThatThrownBy(() -> records.read(foreign))
                .isInstanceOfSatisfying(
                        ResourceAccessDeniedException.class,
                        denied -> {
                            assertThat(denied.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                            assertThat(denied.getCode()).isEqualTo("assessment-not-found");
                        });

        assertThat(records.callCount()).isZero();
        verify(auditTrail)
                .record(
                        AccessAction.READ,
                        ResourceType.ASSESSMENT,
                        foreign,
                        AccessOutcome.DENIED,
                        "not-owner");
    }

    @Test
    void anUnknownRecordLooksTheSameButIsAuditedAsUnknown() {
        UUID unknown = UUID.randomUUID();
        when(resolver.ownerOf(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> records.read(unknown))
                .isInstanceOfSatisfying(
                        ResourceAccessDeniedException.class,
                        denied -> assertThat(denied.getCode()).isEqualTo("assessment-not-found"));

        verify(auditTrail)
                .record(
                        AccessAction.READ,
                        ResourceType.ASSESSMENT,
                        unknown,
                        AccessOutcome.DENIED,
                        "unknown-resource");
    }

    @Test
    void aMissingRequiredIdIsRefused() {
        assertThatThrownBy(() -> records.read(null))
                .isInstanceOfSatisfying(
                        ResourceAccessDeniedException.class,
                        denied -> assertThat(denied.auditReason()).isEqualTo("unknown-resource"));
        assertThat(records.callCount()).isZero();
    }

    @Test
    void aMissingOptionalIdNamesNoRecordAndIsNotChecked() {
        assertThat(records.continueOrStart(new Request(null))).isEqualTo("continued");
        assertThat(records.continueOrStart(null)).isEqualTo("continued");
    }

    @Test
    void anOptionalIdThatIsPresentIsChecked() {
        UUID foreign = UUID.randomUUID();
        when(resolver.ownerOf(foreign)).thenReturn(Optional.of(UUID.randomUUID()));

        assertThat(records.continueOrStart(new Request(RECORD))).isEqualTo("continued");
        assertThatThrownBy(() -> records.continueOrStart(new Request(foreign)))
                .isInstanceOf(ResourceAccessDeniedException.class);
    }

    @Test
    void anIdGivenAsTextIsAcceptedAndAMalformedOneIsAnUnknownRecord() {
        assertThat(records.readByText(RECORD.toString())).isEqualTo("record");
        assertThatThrownBy(() -> records.readByText("not-a-uuid"))
                .isInstanceOfSatisfying(
                        ResourceAccessDeniedException.class,
                        denied -> assertThat(denied.auditReason()).isEqualTo("unknown-resource"));
        assertThatThrownBy(() -> records.readByNumber(7))
                .isInstanceOf(ResourceAccessDeniedException.class);
    }

    @Test
    void aCallerWithoutAProfileIsAskedToCompleteIt() {
        when(users.currentUser()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> records.read(RECORD))
                .isInstanceOfSatisfying(
                        ApiProblemException.class,
                        problem -> assertThat(problem.getCode()).isEqualTo("profile-required"));
    }

    @Test
    void aResourceTypeWithoutAResolverIsAProgrammingError() {
        assertThatThrownBy(() -> records.readProfile(RECORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PATIENT_PROFILE");
    }

    @Test
    void twoResolversForOneTypeAreRefusedAtStartup() {
        OwnershipResolver second = mock(OwnershipResolver.class);
        when(second.resource()).thenReturn(ResourceType.ASSESSMENT);

        assertThatThrownBy(
                        () ->
                                new OwnershipAspect(
                                        users, new MethodExpressions(), List.of(resolver, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("More than one");
    }

    @Test
    void aBadAuditExpressionIsLoggedAndTheCallStillSucceeds() {
        assertThat(records.readWithBadAuditId()).isEqualTo("record");

        verify(auditTrail).record(any(), any(), eq(null), eq(AccessOutcome.ALLOWED), any());
    }

    @Test
    void codesFollowTheResourceType() {
        assertThat(ResourceAccessDeniedException.codeFor(ResourceType.PATIENT_PROFILE))
                .isEqualTo("patient-profile-not-found");
    }
}
