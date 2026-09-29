package com.jagapathi.pharmacy.inventory.api.controller;

import com.jagapathi.pharmacy.inventory.domain.exception.InventoryAuthorizationException;
import com.jagapathi.pharmacy.inventory.domain.exception.ReservationNotFoundException;
import com.jagapathi.pharmacy.inventory.domain.exception.StockLevelAlreadyExistsException;
import com.jagapathi.pharmacy.inventory.domain.exception.StockLevelNotFoundException;
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

    @ExceptionHandler(StockLevelNotFoundException.class)
    public ProblemDetail handleStockLevelNotFound(StockLevelNotFoundException ex, WebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setType(URI.create("https://inventory.example.com/errors/stock-not-found"));
        detail.setTitle("Stock Not Found");
        return detail;
    }

    @ExceptionHandler(StockLevelAlreadyExistsException.class)
    public ProblemDetail handleStockLevelAlreadyExists(StockLevelAlreadyExistsException ex, WebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setType(URI.create("https://inventory.example.com/errors/stock-already-exists"));
        detail.setTitle("Stock Level Already Exists");
        return detail;
    }

    @ExceptionHandler(InventoryAuthorizationException.class)
    public ProblemDetail handleInventoryAuthorization(InventoryAuthorizationException ex, WebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        detail.setType(URI.create("https://inventory.example.com/errors/unauthorized"));
        detail.setTitle("Forbidden");
        return detail;
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    public ProblemDetail handleReservationNotFound(ReservationNotFoundException ex, WebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setType(URI.create("https://inventory.example.com/errors/reservation-not-found"));
        detail.setTitle("Reservation Not Found");
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Validation error: check individual field errors"
        );
        detail.setType(URI.create("https://inventory.example.com/errors/validation-error"));
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
        detail.setType(URI.create("https://inventory.example.com/errors/illegal-argument"));
        detail.setTitle("Invalid Request");
        return detail;
    }
}
