package com.jagapathi.pharmacy.customer.api.controller;

import com.jagapathi.pharmacy.customer.domain.exception.CustomerAccessDeniedException;
import com.jagapathi.pharmacy.customer.domain.exception.CustomerNotFoundException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomerNotFoundException.class)
    public ProblemDetail handleCustomerNotFound(CustomerNotFoundException ex, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        pd.setTitle("Customer Not Found");
        pd.setType(URI.create("https://pharmacy.example.com/errors/customer-not-found"));
        pd.setProperty("errorCode", "CUSTOMER_NOT_FOUND");
        return pd;
    }

    @ExceptionHandler(CustomerAccessDeniedException.class)
    public ProblemDetail handleAccessDenied(CustomerAccessDeniedException ex, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "Access denied"
        );
        pd.setTitle("Access Denied");
        pd.setType(URI.create("https://pharmacy.example.com/errors/access-denied"));
        pd.setProperty("errorCode", "ACCESS_DENIED");
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        String errors = ex.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .toList()
                .toString();

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Validation failed: " + errors
        );
        pd.setTitle("Validation Error");
        pd.setType(URI.create("https://pharmacy.example.com/errors/validation-error"));
        pd.setProperty("errorCode", "VALIDATION_ERROR");
        return pd;
    }
}
