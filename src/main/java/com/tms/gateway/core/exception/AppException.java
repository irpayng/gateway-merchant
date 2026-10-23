package com.tms.gateway.core.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Application-specific exception with HTTP status code.
 */
@Getter
public class AppException extends RuntimeException {

    private final HttpStatus status;

    public AppException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public AppException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }
}
