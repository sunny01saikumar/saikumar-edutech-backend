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
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.util.*;

@Component
public class GeminiProvider implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiProvider.class);

    @Value("${nexus.ai.gemini.api-key:}")
    private String envApiKey;

    @Value("${nexus.ai.gemini.model:gemini-1.5-flash}")
    private String defaultModel;

    @Value("${nexus.ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String baseUrl;

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public GeminiProvider(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "gemini";
    }

    @Override
    public boolean isAvailable() {
        return envApiKey != null && !envApiKey.trim().isEmpty();
    }

    private String getEffectiveKey(AiRequest request) {
        if (request != null && request.getCustomApiKey() != null && !request.getCustomApiKey().trim().isEmpty()) {
            return request.getCustomApiKey().trim();
        }
        return envApiKey;
    }

    private String resolveModel(String requestedModel) {
        if (requestedModel == null || requestedModel.trim().isEmpty()
                || requestedModel.equalsIgnoreCase("default")
                || requestedModel.equalsIgnoreCase("default model")) {
            return "gemini-1.5-flash";
        }
        return requestedModel.trim();
    }

    @Override
    public AiResponse generate(AiRequest request) {
        String apiKey = getEffectiveKey(request);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new AIProviderUnavailableException(getProviderName(), "Google Gemini API Key is missing or empty. Please enter your key in settings or .env.");
        }

        long start = System.currentTimeMillis();
        String model = resolveModel(request.getRequestedModel());
        Map<String, Object> body = buildRequestBody(request);

        WebClient webClient = webClientBuilder.baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        try {
            String jsonResponse;
            try {
                jsonResponse = webClient.post()
                        .uri("/models/" + model + ":generateContent?key=" + apiKey)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
            } catch (WebClientResponseException.NotFound ex) {
                String fallbackModel = model.contains("flash") ? "gemini-1.5-pro" : "gemini-1.5-flash";
                log.warn("Gemini model [{}] returned 404. Retrying with fallback model [{}]", model, fallbackModel);
                jsonResponse = webClient.post()
                        .uri("/models/" + fallbackModel + ":generateContent?key=" + apiKey)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
            }

            long latency = System.currentTimeMillis() - start;
            JsonNode root = objectMapper.readTree(jsonResponse);

            String content = extractTextFromGeminiNode(root);
            int promptTokens = root.path("usageMetadata").path("promptTokenCount").asInt(0);
            int candidatesTokens = root.path("usageMetadata").path("candidatesTokenCount").asInt(0);
            int totalTokens = root.path("usageMetadata").path("totalTokenCount").asInt(0);

            return AiResponse.builder()
                    .id(UUID.randomUUID().toString())
                    .content(content)
                    .provider(getProviderName())
                    .model(model)
                    .usage(new AiUsage(promptTokens, candidatesTokens, totalTokens))
                    .latencyMs(latency)
                    .finishReason("STOP")
                    .build();

        } catch (Exception e) {
            log.error("Gemini API execution error: {}", e.getMessage());
            throw new AIProviderUnavailableException(getProviderName(), e.getMessage());
        }
    }

    @Override
    public Flux<String> stream(AiRequest request) {
        String apiKey = getEffectiveKey(request);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return Flux.error(new AIProviderUnavailableException(getProviderName(), "Google Gemini API Key is missing. Please enter your key in settings or .env."));
        }

        String model = resolveModel(request.getRequestedModel());
        Map<String, Object> body = buildRequestBody(request);

        WebClient webClient = webClientBuilder.baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        return webClient.post()
                .uri("/models/" + model + ":streamGenerateContent?alt=sse&key=" + apiKey)
                .bodyValue(body)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .map(this::extractDeltaToken)
                .filter(token -> !token.isEmpty())
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> {
                    String fallbackModel = model.contains("flash") ? "gemini-1.5-pro" : "gemini-1.5-flash";
                    log.warn("Gemini model [{}] returned 404. Retrying with fallback model [{}]", model, fallbackModel);
                    return webClient.post()
                            .uri("/models/" + fallbackModel + ":streamGenerateContent?alt=sse&key=" + apiKey)
                            .bodyValue(body)
                            .accept(MediaType.TEXT_EVENT_STREAM)
                            .retrieve()
                            .bodyToFlux(String.class)
                            .map(this::extractDeltaToken)
                            .filter(token -> !token.isEmpty());
                });
    }

    private String extractDeltaToken(String sseLine) {
        try {
            String json = sseLine.startsWith("data:") ? sseLine.substring(5).trim() : sseLine.trim();
            if (json.isEmpty()) return "";
            JsonNode root = objectMapper.readTree(json);
            return extractTextFromGeminiNode(root);
        } catch (Exception ignored) {}
        return "";
    }

    private String extractTextFromGeminiNode(JsonNode root) {
        if (root.has("candidates") && root.get("candidates").size() > 0) {
            JsonNode parts = root.get("candidates").get(0).path("content").path("parts");
            if (parts.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode p : parts) {
                    if (p.has("text")) {
                        sb.append(p.get("text").asText());
                    }
                }
                return sb.toString();
            }
        }
        return "";
    }

    private Map<String, Object> buildRequestBody(AiRequest request) {
        Map<String, Object> body = new HashMap<>();

        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
            body.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", request.getSystemPrompt()))
            ));
        }

        List<Map<String, Object>> contents = new ArrayList<>();
        if (request.getMessages() != null) {
            for (AiRequest.ChatMessage msg : request.getMessages()) {
                String role = msg.getRole().equalsIgnoreCase("assistant") ? "model" : "user";
                contents.add(Map.of(
                        "role", role,
                        "parts", List.of(Map.of("text", msg.getContent()))
                ));
            }
        }

        body.put("contents", contents);
        return body;
    }
}
