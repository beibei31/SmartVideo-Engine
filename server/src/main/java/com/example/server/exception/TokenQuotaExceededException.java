package com.example.server.exception;

public class TokenQuotaExceededException extends RuntimeException {

    public TokenQuotaExceededException(String message) {
        super(message);
    }
}
