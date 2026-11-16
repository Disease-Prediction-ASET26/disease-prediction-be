package com.diseaseprediction.security;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes RFC 9457 problem responses from security filters, which run before Spring MVC and its
 * exception handlers. The same shape as {@code ProblemExceptionHandler}: {@code type} is {@code
 * urn:pocket-doc:problem:<code>}, plus a {@code code} property for clients.
 */
public final class ProblemResponseWriter {

    public static final String TYPE_PREFIX = "urn:pocket-doc:problem:";

    private ProblemResponseWriter() {}

    /**
     * @param code a fixed problem code (lowercase letters and dashes)
     * @param detail a fixed message; never request data or tokens
     */
    public static void write(
            HttpServletResponse response, HttpStatus status, String code, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter()
                .write(
                        "{\"type\":\""
                                + json(TYPE_PREFIX + code)
                                + "\",\"title\":\""
                                + json(status.getReasonPhrase())
                                + "\",\"status\":"
                                + status.value()
                                + ",\"detail\":\""
                                + json(detail)
                                + "\",\"code\":\""
                                + json(code)
                                + "\"}");
    }

    private static String json(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
