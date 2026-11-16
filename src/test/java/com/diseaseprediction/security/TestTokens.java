package com.diseaseprediction.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Builds Hanko-shaped session tokens for tests, valid by default. */
public final class TestTokens {

    private String subject = UUID.randomUUID().toString();
    private String sessionId = UUID.randomUUID().toString();
    private String issuer = FakeHanko.ISSUER;
    private String audience = FakeHanko.AUDIENCE;
    private String email = "patient@example.com";
    private boolean emailVerified = true;
    private boolean includeEmail = true;
    private Instant expiresAt = Instant.now().plus(Duration.ofHours(1));
    private RSAKey signingKey = FakeHanko.get().signingKey();

    private TestTokens() {}

    public static TestTokens token() {
        return new TestTokens();
    }

    public TestTokens subject(String subject) {
        this.subject = subject;
        return this;
    }

    public TestTokens sessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public TestTokens issuer(String issuer) {
        this.issuer = issuer;
        return this;
    }

    public TestTokens audience(String audience) {
        this.audience = audience;
        return this;
    }

    public TestTokens email(String email, boolean verified) {
        this.email = email;
        this.emailVerified = verified;
        this.includeEmail = true;
        return this;
    }

    public TestTokens withoutEmail() {
        this.includeEmail = false;
        return this;
    }

    public TestTokens expiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
        return this;
    }

    /** Signs with a fresh key that is not in the fake Hanko's JWKS. */
    public TestTokens signedWithUnknownKey() {
        try {
            this.signingKey = new RSAKeyGenerator(2048).keyID("fake-hanko-key").generate();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
        return this;
    }

    public String subjectValue() {
        return subject;
    }

    /** RS256, like Hanko. */
    public String build() {
        try {
            SignedJWT jwt =
                    new SignedJWT(
                            new JWSHeader.Builder(JWSAlgorithm.RS256)
                                    .keyID(signingKey.getKeyID())
                                    .build(),
                            claims());
            jwt.sign(new RSASSASigner(signingKey));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** Same claims, signed with HMAC-SHA256: must be rejected, Hanko only uses RS256. */
    public String buildHs256() {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims());
            jwt.sign(new MACSigner(new byte[32]));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** Same claims, unsigned ({@code alg: none}): must be rejected. */
    public String buildUnsigned() {
        return new PlainJWT(claims()).serialize();
    }

    private JWTClaimsSet claims() {
        Instant issuedAt = expiresAt.minus(Duration.ofHours(1));
        JWTClaimsSet.Builder builder =
                new JWTClaimsSet.Builder()
                        .subject(subject)
                        .issuer(issuer)
                        .audience(audience)
                        .issueTime(Date.from(issuedAt))
                        .expirationTime(Date.from(expiresAt))
                        .claim("session_id", sessionId);
        if (includeEmail) {
            Map<String, Object> emailClaim = new LinkedHashMap<>();
            emailClaim.put("address", email);
            emailClaim.put("is_primary", true);
            emailClaim.put("is_verified", emailVerified);
            builder.claim("email", emailClaim);
        }
        return builder.build();
    }
}
