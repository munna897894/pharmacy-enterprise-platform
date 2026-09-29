package com.jagapathi.pharmacy.order.api.controller;

import com.jagapathi.pharmacy.order.domain.InvalidOrderStateException;
import com.jagapathi.pharmacy.order.domain.OrderAuthorizationException;
import com.jagapathi.pharmacy.order.domain.OrderNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail handleOrderNotFound(OrderNotFoundException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            ex.getMessage()
        );
        problem.setType(URI.create("https://errors.pharmacy.local/order-not-found"));
        problem.setTitle("Order Not Found");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("correlationId", UUID.randomUUID().toString());
        return problem;
    }

    @ExceptionHandler(OrderAuthorizationException.class)
    public ProblemDetail handleOrderUnauthorized(OrderAuthorizationException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN,
            ex.getMessage()
        );
        problem.setType(URI.create("https://errors.pharmacy.local/order-unauthorized"));
        problem.setTitle("Order Access Denied");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("correlationId", UUID.randomUUID().toString());
        return problem;
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ProblemDetail handleInvalidOrderState(InvalidOrderStateException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            ex.getMessage()
        );
        problem.setType(URI.create("https://errors.pharmacy.local/invalid-order-state"));
        problem.setTitle("Invalid Order State");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("correlationId", UUID.randomUUID().toString());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            ex.getMessage()
        );
        problem.setType(URI.create("https://errors.pharmacy.local/invalid-argument"));
        problem.setTitle("Invalid Argument");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("correlationId", UUID.randomUUID().toString());
        return problem;
    }

    @ExceptionHandler(com.jagapathi.pharmacy.order.domain.IdempotencyConflictException.class)
    public ProblemDetail handleIdempotencyConflict(
            com.jagapathi.pharmacy.order.domain.IdempotencyConflictException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            ex.getMessage()
        );
        problem.setType(URI.create("https://errors.pharmacy.local/idempotency-conflict"));
        problem.setTitle("Idempotency Key Conflict");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("correlationId", UUID.randomUUID().toString());
        return problem;
    }
}
