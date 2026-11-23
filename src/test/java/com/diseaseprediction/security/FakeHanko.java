package com.diseaseprediction.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A stand-in for Hanko's public API in tests: serves the JWKS with one RSA signing key and answers
 * {@code POST /sessions/validate}. Started once per JVM so every Spring test context shares it.
 */
public final class FakeHanko {

    public static final String ISSUER = "https://identity.test";
    public static final String AUDIENCE = "pocket-doc-test";

    /** How the fake answers the session check. */
    public enum SessionMode {
        VALID,
        INVALID,
        ERROR
    }

    private static final FakeHanko INSTANCE = new FakeHanko();

    private final RSAKey signingKey;
    private final HttpServer server;
    private final List<String> validatedTokens = new CopyOnWriteArrayList<>();
    private volatile SessionMode sessionMode = SessionMode.VALID;

    private FakeHanko() {
        try {
            signingKey = new RSAKeyGenerator(2048).keyID("fake-hanko-key").generate();
            server =
                    HttpServer.create(
                            new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot start the fake Hanko", exception);
        }
        String jwks = new JWKSet(signingKey.toPublicJWK()).toString();
        server.createContext("/.well-known/jwks.json", exchange -> respond(exchange, 200, jwks));
        server.createContext("/sessions/validate", this::validate);
        server.start();
    }

    public static FakeHanko get() {
        return INSTANCE;
    }

    public String baseUrl() {
        return "http://"
                + server.getAddress().getHostString()
                + ":"
                + server.getAddress().getPort();
    }

    public RSAKey signingKey() {
        return signingKey;
    }

    public void sessionMode(SessionMode mode) {
        this.sessionMode = mode;
    }

    /** Tokens received by {@code /sessions/validate}, in order. */
    public List<String> validatedTokens() {
        return List.copyOf(validatedTokens);
    }

    public void reset() {
        sessionMode = SessionMode.VALID;
        validatedTokens.clear();
    }

    private void validate(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        validatedTokens.add(body);
        switch (sessionMode) {
            case VALID -> respond(exchange, 200, "{\"is_valid\":true}");
            case INVALID -> respond(exchange, 200, "{\"is_valid\":false}");
            case ERROR ->
                    respond(exchange, 500, "{\"code\":500,\"message\":\"Internal Server Error\"}");
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) {
        try (exchange) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
