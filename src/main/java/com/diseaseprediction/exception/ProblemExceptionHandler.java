package com.diseaseprediction.exception;

import com.diseaseprediction.security.ProblemResponseWriter;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/** Maps {@link ApiProblemException} to {@code application/problem+json}. */
@RestControllerAdvice
public class ProblemExceptionHandler {

    @ExceptionHandler(ApiProblemException.class)
    ResponseEntity<ProblemDetail> handle(ApiProblemException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        problem.setType(URI.create(ProblemResponseWriter.TYPE_PREFIX + exception.getCode()));
        problem.setTitle(exception.getStatus().getReasonPhrase());
        problem.setProperty("code", exception.getCode());
        return ResponseEntity.status(exception.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
