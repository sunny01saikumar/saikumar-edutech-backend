package com.nexus.ai.orchestrator;

import com.nexus.ai.provider.AIProvider;
import com.nexus.ai.provider.ProviderRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ModelRouter {

    private final ProviderRegistry providerRegistry;

    public ModelRouter(ProviderRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    public static class RouteDecision {
        private final AIProvider provider;
        private final String targetModel;
        private final String category;

        public RouteDecision(AIProvider provider, String targetModel, String category) {
            this.provider = provider;
            this.targetModel = targetModel;
            this.category = category;
        }

        public AIProvider getProvider() { return provider; }
        public String getTargetModel() { return targetModel; }
        public String getCategory() { return category; }
    }

    public RouteDecision route(String prompt, String requestedProvider, String requestedModel) {
        // If explicit provider requested, try to use it
        if (requestedProvider != null && !requestedProvider.isEmpty() && !requestedProvider.equalsIgnoreCase("auto")) {
            AIProvider provider = providerRegistry.getProvider(requestedProvider);
            return new RouteDecision(provider, requestedModel, "explicit");
        }

        AIProvider defaultProvider = providerRegistry.getDefaultProvider();
        String category = classifyTask(prompt);

        // Map category to best available model for default provider
        String selectedModel = requestedModel;
        if (selectedModel == null || selectedModel.isEmpty()) {
            selectedModel = selectBestModelForProvider(defaultProvider.getProviderName(), category);
        }

        return new RouteDecision(defaultProvider, selectedModel, category);
    }

    private String classifyTask(String prompt) {
        if (prompt == null) return "simple";
        String lower = prompt.toLowerCase();
        if (lower.contains("code") || lower.contains("class ") || lower.contains("function") ||
            lower.contains("java") || lower.contains("python") || lower.contains("bug") ||
            lower.contains("react") || lower.contains("refactor") || lower.contains("script")) {
            return "coding";
        }
        if (lower.contains("explain") || lower.contains("reason") || lower.contains("architecture") ||
            lower.contains("compare") || lower.contains("solve") || lower.contains("proof")) {
            return "reasoning";
        }
        if (lower.length() > 3000 || lower.contains("document") || lower.contains("pdf")) {
            return "long-context";
        }
        return "simple";
    }

    private String selectBestModelForProvider(String providerName, String category) {
        switch (providerName.toLowerCase()) {
            case "openai":
                return "coding".equals(category) || "reasoning".equals(category) ? "gpt-4o" : "gpt-4o-mini";
            case "anthropic":
                return "claude-3-5-sonnet-20241022";
            case "gemini":
                return "gemini-1.5-pro";
            case "ollama":
                return "llama3";
            default:
                return null;
        }
    }
}
