package com.example.settlement.core.domain.exception;

public class PolicyConflictException extends RuntimeException {
    public PolicyConflictException(String message) {
        super(message);
    }
}
