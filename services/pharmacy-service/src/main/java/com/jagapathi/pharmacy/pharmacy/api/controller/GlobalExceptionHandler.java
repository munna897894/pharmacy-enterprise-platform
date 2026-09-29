package com.jagapathi.pharmacy.pharmacy.api.controller;

import com.jagapathi.pharmacy.pharmacy.domain.exception.PharmacyAuthorizationException;
import com.jagapathi.pharmacy.pharmacy.domain.exception.PharmacyNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PharmacyNotFoundException.class)
    public ProblemDetail handlePharmacyNotFound(PharmacyNotFoundException ex, WebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setType(URI.create("https://pharmacy.example.com/errors/pharmacy-not-found"));
        detail.setTitle("Pharmacy Not Found");
        return detail;
    }

    @ExceptionHandler(PharmacyAuthorizationException.class)
    public ProblemDetail handlePharmacyAuthorization(PharmacyAuthorizationException ex, WebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        detail.setType(URI.create("https://pharmacy.example.com/errors/unauthorized"));
        detail.setTitle("Forbidden");
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Validation error: check individual field errors"
        );
        detail.setType(URI.create("https://pharmacy.example.com/errors/validation-error"));
        detail.setTitle("Validation Error");
        detail.setProperty("errorCode", "VALIDATION_ERROR");
        
        var fieldErrors = ex.getBindingResult().getAllErrors().stream()
                .filter(error -> error instanceof FieldError)
                .map(error -> {
                    FieldError fieldError = (FieldError) error;
                    return fieldError.getField() + ": " + fieldError.getDefaultMessage();
                })
                .toList();
        
        if (!fieldErrors.isEmpty()) {
            detail.setProperty("fieldErrors", fieldErrors);
        }
        
        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );
        detail.setType(URI.create("https://pharmacy.example.com/errors/illegal-argument"));
        detail.setTitle("Invalid Request");
        return detail;
    }
}
