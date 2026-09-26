package com.nexus.ai.provider;

public class AIProviderUnavailableException extends RuntimeException {
    private final String providerName;

    public AIProviderUnavailableException(String message) {
        super(message);
        this.providerName = "UNKNOWN";
    }

    public AIProviderUnavailableException(String providerName, String message) {
        super("Provider [" + providerName + "] unavailable: " + message);
        this.providerName = providerName;
    }

    public String getProviderName() {
        return providerName;
    }
}
