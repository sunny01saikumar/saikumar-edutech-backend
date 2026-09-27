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
        // Priority 1: Groq if Groq key (or gsk_ key) configured on Render
        AIProvider groq = providers.get("groq");
        if (groq != null && groq.isAvailable()) return groq;

        // Priority 2: Gemini if key configured on Render
        AIProvider gemini = providers.get("gemini");
        if (gemini != null && gemini.isAvailable()) return gemini;

        // Priority 3: OpenAI if key configured on Render
        AIProvider openai = providers.get("openai");
        if (openai != null && openai.isAvailable()) return openai;

        // Priority 4: Anthropic if key configured on Render
        AIProvider anthropic = providers.get("anthropic");
        if (anthropic != null && anthropic.isAvailable()) return anthropic;

        // Priority 5: Ollama if local tunnel is active
        AIProvider ollama = providers.get("ollama");
        if (ollama != null && ollama.isAvailable()) return ollama;

        // Priority 6: Configured default if available
        AIProvider configured = providers.get(configuredDefaultProvider.toLowerCase());
        if (configured != null && configured.isAvailable()) return configured;

        // Fallbacks
        if (groq != null) return groq;
        if (gemini != null) return gemini;
        if (ollama != null) return ollama;

        throw new AIProviderUnavailableException("none",
                "No AI provider key is configured on Render. Please add GROQ_API_KEY or GEMINI_API_KEY in Render Environment Variables for 24/7 availability.");
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
