package com.jagapathi.pharmacy.product.api.advice;

import com.jagapathi.pharmacy.product.domain.exception.DuplicateNdcCodeException;
import com.jagapathi.pharmacy.product.domain.exception.MedicationNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MedicationNotFoundException.class)
    public ProblemDetail handleMedicationNotFound(MedicationNotFoundException ex) {
        log.debug("Medication not found: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setProperty("errorCode", "MEDICATION_NOT_FOUND");
        detail.setProperty("timestamp", Instant.now());
        return detail;
    }

    @ExceptionHandler(DuplicateNdcCodeException.class)
    public ProblemDetail handleDuplicateNdcCode(DuplicateNdcCodeException ex) {
        log.debug("Duplicate NDC code: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setProperty("errorCode", "DUPLICATE_NDC_CODE");
        detail.setProperty("timestamp", Instant.now());
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationError(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );
        log.debug("Validation error: {}", fieldErrors);

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        detail.setProperty("errorCode", "VALIDATION_ERROR");
        detail.setProperty("fieldErrors", fieldErrors);
        detail.setProperty("timestamp", Instant.now());
        return detail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        detail.setProperty("errorCode", "INTERNAL_ERROR");
        detail.setProperty("timestamp", Instant.now());
        return detail;
    }
}
