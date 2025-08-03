package com.ooter.backend.exception;

public class AuthException extends RuntimeException {
    public AuthException(String message) {
        super(message);
    }
}