package com.diseaseprediction.security;

import com.diseaseprediction.TestcontainersConfiguration;
import com.diseaseprediction.service.prediction.PredictionClient;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base for full-stack API tests: real security filter chain and JWT decoder pointed at {@link
 * FakeHanko}, real PostgreSQL from Testcontainers, prediction service mocked. Subclasses share one
 * Spring context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class AuthenticatedApiTest {

    @Autowired protected MockMvc mockMvc;

    @MockitoBean protected PredictionClient predictionClient;

    protected final FakeHanko hanko = FakeHanko.get();

    @DynamicPropertySource
    static void hankoProperties(DynamicPropertyRegistry registry) {
        FakeHanko hanko = FakeHanko.get();
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> FakeHanko.ISSUER);
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> hanko.baseUrl() + "/.well-known/jwks.json");
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.audiences", () -> FakeHanko.AUDIENCE);
        registry.add("app.auth.hanko.api-url", hanko::baseUrl);
    }

    @BeforeEach
    void resetFakeHanko() {
        hanko.reset();
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
