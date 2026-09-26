package com.nexus.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.domain.AiRequest;
import com.nexus.ai.domain.AiResponse;
import com.nexus.ai.domain.AiUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.*;

@Component
public class AnthropicProvider implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicProvider.class);

    @Value("${nexus.ai.anthropic.api-key:}")
    private String apiKey;

    @Value("${nexus.ai.anthropic.model:claude-3-5-sonnet-20241022}")
    private String defaultModel;

    @Value("${nexus.ai.anthropic.base-url:https://api.anthropic.com/v1}")
    private String baseUrl;

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public AnthropicProvider(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "anthropic";
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    private String getEffectiveKey(AiRequest request) {
        if (request != null && request.getCustomApiKey() != null && !request.getCustomApiKey().trim().isEmpty()) {
            return request.getCustomApiKey().trim();
        }
        return apiKey;
    }

    @Override
    public AiResponse generate(AiRequest request) {
        String effectiveApiKey = getEffectiveKey(request);
        if (effectiveApiKey == null || effectiveApiKey.trim().isEmpty()) {
            throw new AIProviderUnavailableException(getProviderName(), "Anthropic API Key is missing or empty. Please enter your key in settings or .env.");
        }

        long start = System.currentTimeMillis();
        String model = (request.getRequestedModel() != null && !request.getRequestedModel().isEmpty())
                ? request.getRequestedModel() : defaultModel;

        Map<String, Object> body = buildRequestBody(request, model, false);

        WebClient webClient = webClientBuilder.baseUrl(baseUrl)
                .defaultHeader("x-api-key", effectiveApiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        try {
            String jsonResponse = webClient.post()
                    .uri("/messages")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            long latency = System.currentTimeMillis() - start;
            JsonNode root = objectMapper.readTree(jsonResponse);

            StringBuilder content = new StringBuilder();
            if (root.has("content") && root.get("content").isArray()) {
                for (JsonNode item : root.get("content")) {
                    if ("text".equals(item.path("type").asText())) {
                        content.append(item.path("text").asText());
                    }
                }
            }

            int inputTokens = root.path("usage").path("input_tokens").asInt(0);
            int outputTokens = root.path("usage").path("output_tokens").asInt(0);

            return AiResponse.builder()
                    .id(root.path("id").asText(UUID.randomUUID().toString()))
                    .content(content.toString())
                    .provider(getProviderName())
                    .model(model)
                    .usage(new AiUsage(inputTokens, outputTokens, inputTokens + outputTokens))
                    .latencyMs(latency)
                    .finishReason(root.path("stop_reason").asText("end_turn"))
                    .build();

        } catch (Exception e) {
            log.error("Anthropic API execution error: {}", e.getMessage());
            throw new AIProviderUnavailableException(getProviderName(), e.getMessage());
        }
    }

    @Override
    public Flux<String> stream(AiRequest request) {
        String effectiveApiKey = getEffectiveKey(request);
        if (effectiveApiKey == null || effectiveApiKey.trim().isEmpty()) {
            return Flux.error(new AIProviderUnavailableException(getProviderName(), "Anthropic API Key is missing. Please enter your key in settings or .env."));
        }

        String model = (request.getRequestedModel() != null && !request.getRequestedModel().isEmpty())
                ? request.getRequestedModel() : defaultModel;

        Map<String, Object> body = buildRequestBody(request, model, true);

        WebClient webClient = webClientBuilder.baseUrl(baseUrl)
                .defaultHeader("x-api-key", effectiveApiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        return webClient.post()
                .uri("/messages")
                .bodyValue(body)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .map(this::extractDeltaToken)
                .filter(token -> !token.isEmpty());
    }

    private String extractDeltaToken(String sseLine) {
        try {
            if (!sseLine.startsWith("data:")) return "";
            String json = sseLine.substring(5).trim();
            JsonNode root = objectMapper.readTree(json);
            if ("content_block_delta".equals(root.path("type").asText())) {
                JsonNode delta = root.path("delta");
                if ("text_delta".equals(delta.path("type").asText())) {
                    return delta.path("text").asText();
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    private Map<String, Object> buildRequestBody(AiRequest request, String model, boolean stream) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 4096);
        body.put("stream", stream);

        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
            body.put("system", request.getSystemPrompt());
        }

        List<Map<String, String>> messages = new ArrayList<>();
        if (request.getMessages() != null) {
            for (AiRequest.ChatMessage msg : request.getMessages()) {
                String role = msg.getRole().equalsIgnoreCase("system") ? "user" : msg.getRole().toLowerCase();
                messages.add(Map.of("role", role, "content", msg.getContent()));
            }
        }

        body.put("messages", messages);
        return body;
    }
}
