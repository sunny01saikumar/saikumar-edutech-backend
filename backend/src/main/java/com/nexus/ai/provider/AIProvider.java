package com.nexus.ai.provider;

import com.nexus.ai.domain.AiRequest;
import com.nexus.ai.domain.AiResponse;
import reactor.core.publisher.Flux;

public interface AIProvider {

    AiResponse generate(AiRequest request);

    Flux<String> stream(AiRequest request);

    String getProviderName();

    boolean isAvailable();
}
