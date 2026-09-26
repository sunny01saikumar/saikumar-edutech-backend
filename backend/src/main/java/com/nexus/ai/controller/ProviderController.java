package com.nexus.ai.controller;

import com.nexus.ai.provider.ProviderRegistry;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/providers")
public class ProviderController {

    private final ProviderRegistry providerRegistry;

    public ProviderController(ProviderRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    @GetMapping
    public Map<String, Object> getProviders() {
        return providerRegistry.getProviderStatusMap();
    }
}
