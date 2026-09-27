package com.nexus.ai.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProviderRegistry.class);

    private final Map<String, AIProvider> providers = new HashMap<>();

    @Value("${nexus.ai.provider:ollama}")
    private String configuredDefaultProvider;

    public ProviderRegistry(List<AIProvider> providerList) {
        for (AIProvider provider : providerList) {
            providers.put(provider.getProviderName().toLowerCase(), provider);
            log.info("Registered AI Provider: [{}] (Available: {})", provider.getProviderName(), provider.isAvailable());
        }
    }

    public AIProvider getProvider(String name) {
        if (name == null || name.trim().isEmpty() || name.equalsIgnoreCase("auto")) {
            return getDefaultProvider();
        }
        AIProvider provider = providers.get(name.toLowerCase());
        if (provider == null) {
            throw new AIProviderUnavailableException(name, "Unknown or unsupported provider specified: " + name);
        }
        return provider;
    }

    public AIProvider getDefaultProvider() {
        // Priority 1: Configured default provider (e.g. Gemini if AI_PROVIDER=gemini)
        AIProvider configured = providers.get(configuredDefaultProvider.toLowerCase());
        if (configured != null && configured.isAvailable()) {
            return configured;
        }

        // Priority 2: Gemini if available
        AIProvider gemini = providers.get("gemini");
        if (gemini != null && gemini.isAvailable()) return gemini;

        // Priority 3: OpenAI if available
        AIProvider openai = providers.get("openai");
        if (openai != null && openai.isAvailable()) return openai;

        // Priority 4: Anthropic if available
        AIProvider anthropic = providers.get("anthropic");
        if (anthropic != null && anthropic.isAvailable()) return anthropic;

        // Priority 5: Fallback to configured or Ollama
        if (configured != null) return configured;

        AIProvider ollama = providers.get("ollama");
        if (ollama != null) return ollama;

        for (AIProvider p : providers.values()) {
            return p;
        }

        throw new AIProviderUnavailableException(configuredDefaultProvider,
                "No AI provider is configured or available.");
    }

    public Map<String, Object> getProviderStatusMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> list = new ArrayList<>();

        for (Map.Entry<String, AIProvider> entry : providers.entrySet()) {
            AIProvider p = entry.getValue();
            Map<String, Object> info = new HashMap<>();
            info.put("name", p.getProviderName());
            info.put("available", p.isAvailable());
            info.put("isDefault", p.getProviderName().equalsIgnoreCase(configuredDefaultProvider));
            list.add(info);
        }

        result.put("defaultProvider", configuredDefaultProvider);
        result.put("providers", list);
        return result;
    }
}
