package com.recipemanager.api.exception;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Intercepts exceptions thrown from any @RestController in the application.
// C# equivalent: IExceptionFilter or a middleware catch-all in Program.cs.
// Methods are matched by exception type — most specific match wins.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Handles our own domain exceptions. The status code travels with the exception
    // so the service layer decides the semantics, not this class.
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        return ResponseEntity
                .status(ex.getStatus())
                .body(new ApiError(ex.getMessage(), ex.getStatus().value()));
    }

    // JPA throws EntityNotFoundException when findById (or similar) finds no row.
    // Catching it here keeps 404 logic out of every service method.
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleEntityNotFound(EntityNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiError(ex.getMessage(), HttpStatus.NOT_FOUND.value()));
    }

    // Catch-all: anything not matched by a more specific handler lands here.
    // Log the exception in production; returning the raw message is fine for now.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("An unexpected error occurred", 500));
    }
}
