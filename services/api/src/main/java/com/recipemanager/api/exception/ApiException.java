package com.recipemanager.api.exception;

import org.springframework.http.HttpStatus;

// Throw this from the service layer when a known error condition occurs.
// It carries an HTTP status so GlobalExceptionHandler can map it without a giant if-else.
// C# equivalent: a custom exception that your IExceptionFilter inspects.
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
