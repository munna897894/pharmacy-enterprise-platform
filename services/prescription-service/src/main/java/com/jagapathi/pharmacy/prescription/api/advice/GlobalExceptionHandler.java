package com.jagapathi.pharmacy.prescription.api.advice;

import com.jagapathi.pharmacy.prescription.domain.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(PrescriptionNotFoundException.class)
    public ProblemDetail handlePrescriptionNotFound(PrescriptionNotFoundException ex, WebRequest request) {
        log.debug("Prescription not found: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setType(URI.create("https://errors.pharmacy.local/prescription-not-found"));
        detail.setTitle("Prescription Not Found");
        return detail;
    }
    
    @ExceptionHandler(PrescriptionLineNotFoundException.class)
    public ProblemDetail handlePrescriptionLineNotFound(PrescriptionLineNotFoundException ex, WebRequest request) {
        log.debug("Prescription line not found: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setType(URI.create("https://errors.pharmacy.local/prescription-line-not-found"));
        detail.setTitle("Prescription Line Not Found");
        return detail;
    }
    
    @ExceptionHandler(InvalidPrescriptionStateException.class)
    public ProblemDetail handleInvalidPrescriptionState(InvalidPrescriptionStateException ex, WebRequest request) {
        log.debug("Invalid prescription state: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setType(URI.create("https://errors.pharmacy.local/invalid-state"));
        detail.setTitle("Invalid State");
        return detail;
    }
    
    @ExceptionHandler(CustomerValidationException.class)
    public ProblemDetail handleCustomerValidation(CustomerValidationException ex, WebRequest request) {
        log.debug("Customer validation failed: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setType(URI.create("https://errors.pharmacy.local/validation-error"));
        detail.setTitle("Validation Error");
        return detail;
    }
    
    @ExceptionHandler(PrescriberValidationException.class)
    public ProblemDetail handlePrescriberValidation(PrescriberValidationException ex, WebRequest request) {
        log.debug("Prescriber validation failed: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setType(URI.create("https://errors.pharmacy.local/validation-error"));
        detail.setTitle("Validation Error");
        return detail;
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        log.debug("Validation failed: {}", ex.getMessage());
        String errors = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, errors);
        detail.setType(URI.create("https://errors.pharmacy.local/validation-error"));
        detail.setTitle("Validation Error");
        return detail;
    }
}
