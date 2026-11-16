package com.diseaseprediction.security;

import com.diseaseprediction.repository.UserRepository;
import com.diseaseprediction.security.hanko.HankoProperties;
import com.diseaseprediction.security.hanko.HankoSessionCheckFilter;
import com.diseaseprediction.security.hanko.HankoSessionClient;

import jakarta.servlet.DispatcherType;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Clock;

/**
 * API security (MDD Iteration 6). Every request needs a Hanko session JWT in {@code Authorization:
 * Bearer}. Spring's resource server checks the signature (RS256 against Hanko's JWKS), expiry,
 * issuer and audience from the {@code spring.security.oauth2.resourceserver.jwt.*} properties;
 * {@link HankoSessionCheckFilter} then checks with Hanko that the session is still active.
 *
 * <p>Stateless: no HTTP session and no cookies, so CSRF protection is not needed.
 */
@Configuration
@EnableConfigurationProperties(HankoProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            UserRepository userRepository,
            HankoSessionClient hankoSessionClient,
            HankoProperties hankoProperties)
            throws Exception {
        RoleAuthoritiesConverter roleAuthoritiesConverter =
                new RoleAuthoritiesConverter(userRepository);
        ProblemAuthenticationEntryPoint entryPoint = new ProblemAuthenticationEntryPoint();
        ProblemAccessDeniedHandler accessDeniedHandler = new ProblemAccessDeniedHandler();
        HankoSessionCheckFilter sessionCheckFilter =
                new HankoSessionCheckFilter(
                        hankoSessionClient,
                        entryPoint,
                        hankoProperties.sessionCheckTtl(),
                        Clock.systemUTC());

        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                        requests ->
                                requests
                                        // Error pages for already-checked requests (e.g. a 502).
                                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/me/consents",
                                                "/api/assessments")
                                        .hasRole("PATIENT")
                                        .requestMatchers("/api/me")
                                        .authenticated()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        resourceServer ->
                                resourceServer
                                        .jwt(
                                                jwt ->
                                                        jwt.jwtAuthenticationConverter(
                                                                roleAuthoritiesConverter))
                                        .authenticationEntryPoint(entryPoint)
                                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(entryPoint)
                                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterAfter(sessionCheckFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
