package com.jagapathi.pharmacy.auth.api.exception;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.jagapathi.pharmacy.auth.application.exception.AuthException;
import com.jagapathi.pharmacy.auth.application.exception.InvalidCredentialsException;
import com.jagapathi.pharmacy.auth.application.exception.InvalidTokenException;
import com.jagapathi.pharmacy.auth.application.exception.UserAlreadyExistsException;
import com.jagapathi.pharmacy.auth.application.exception.UserNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCredentials(InvalidCredentialsException e) {
        logger.debug("Invalid credentials: {}", e.getMessage());

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Invalid credentials"
        );
        detail.setProperty("errorCode", "INVALID_CREDENTIALS");
        detail.setProperty("timestamp", Instant.now().toString());
        detail.setProperty("correlationId", UUID.randomUUID().toString());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(detail);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ProblemDetail> handleUserAlreadyExists(UserAlreadyExistsException e) {
        logger.warn("User already exists: {}", e.getMessage());

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                e.getMessage()
        );
        detail.setProperty("errorCode", "USER_ALREADY_EXISTS");
        detail.setProperty("timestamp", Instant.now().toString());
        detail.setProperty("correlationId", UUID.randomUUID().toString());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(detail);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleUserNotFound(UserNotFoundException e) {
        logger.debug("User not found: {}", e.getMessage());

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                e.getMessage()
        );
        detail.setProperty("errorCode", "USER_NOT_FOUND");
        detail.setProperty("timestamp", Instant.now().toString());
        detail.setProperty("correlationId", UUID.randomUUID().toString());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(detail);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ProblemDetail> handleInvalidToken(InvalidTokenException e) {
        logger.debug("Invalid token: {}", e.getMessage());

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Invalid or expired token"
        );
        detail.setProperty("errorCode", "INVALID_TOKEN");
        detail.setProperty("timestamp", Instant.now().toString());
        detail.setProperty("correlationId", UUID.randomUUID().toString());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(detail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(MethodArgumentNotValidException e) {
        logger.debug("Validation error: {}", e.getMessage());

        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Validation failed"
        );
        detail.setProperty("errors", errors);
        detail.setProperty("errorCode", "VALIDATION_ERROR");
        detail.setProperty("timestamp", Instant.now().toString());
        detail.setProperty("correlationId", UUID.randomUUID().toString());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(detail);
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ProblemDetail> handleAuthException(AuthException e) {
        logger.error("Auth exception: {}", e.getMessage());

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Authentication error"
        );
        detail.setProperty("errorCode", "AUTH_ERROR");
        detail.setProperty("timestamp", Instant.now().toString());
        detail.setProperty("correlationId", UUID.randomUUID().toString());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(detail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException e) {
        logger.debug("Illegal argument: {}", e.getMessage());

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                e.getMessage()
        );
        detail.setProperty("errorCode", "INVALID_ARGUMENT");
        detail.setProperty("timestamp", Instant.now().toString());
        detail.setProperty("correlationId", UUID.randomUUID().toString());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(detail);
    }
}
