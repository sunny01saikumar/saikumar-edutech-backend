package com.nexus.ai.provider;

public class QuotaExceededException extends RuntimeException {
    private final String userId;

    public QuotaExceededException(String userId, String message) {
        super(message);
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }
}
