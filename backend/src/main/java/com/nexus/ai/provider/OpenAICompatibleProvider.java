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

import java.time.Duration;
import java.util.*;

@Component
public class OpenAICompatibleProvider implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAICompatibleProvider.class);

    @Value("${nexus.ai.openai-compatible.base-url:http://localhost:1234/v1}")
    private String baseUrl;

    @Value("${nexus.ai.openai-compatible.api-key:}")
    private String apiKey;

    @Value("${nexus.ai.openai-compatible.model:local-model}")
    private String defaultModel;

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public OpenAICompatibleProvider(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "openai-compatible";
    }

    private String getEffectiveKey(AiRequest request) {
        if (request != null && request.getCustomApiKey() != null && !request.getCustomApiKey().trim().isEmpty()) {
            return request.getCustomApiKey().trim();
        }
        return apiKey;
    }

    @Override
    public boolean isAvailable() {
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            return true;
        }
        try {
            WebClient webClient = webClientBuilder.baseUrl(baseUrl).build();
            String res = webClient.get()
                    .uri("/models")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(1))
                    .block();
            return res != null && res.contains("data");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public AiResponse generate(AiRequest request) {
        if (!isAvailable()) {
            throw new AIProviderUnavailableException(getProviderName(),
                    "OpenAI-Compatible endpoint is not reachable at " + baseUrl);
        }

        long start = System.currentTimeMillis();
        String model = (request.getRequestedModel() != null && !request.getRequestedModel().isEmpty())
                ? request.getRequestedModel() : defaultModel;

        Map<String, Object> body = buildRequestBody(request, model, false);

        WebClient.Builder builder = webClientBuilder.baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        WebClient webClient = builder.build();

        try {
            String jsonResponse = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            long latency = System.currentTimeMillis() - start;
            JsonNode root = objectMapper.readTree(jsonResponse);

            String content = "";
            if (root.has("choices") && root.get("choices").size() > 0) {
                JsonNode choice = root.get("choices").get(0);
                if (choice.has("message") && choice.get("message").has("content")) {
                    content = choice.get("message").get("content").asText();
                }
            }

            int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
            int completionTokens = root.path("usage").path("completion_tokens").asInt(0);
            int totalTokens = root.path("usage").path("total_tokens").asInt(0);

            return AiResponse.builder()
                    .id(root.path("id").asText(UUID.randomUUID().toString()))
                    .content(content)
                    .provider(getProviderName())
                    .model(model)
                    .usage(new AiUsage(promptTokens, completionTokens, totalTokens))
                    .latencyMs(latency)
                    .finishReason(root.path("choices").path(0).path("finish_reason").asText("stop"))
                    .build();

        } catch (Exception e) {
            log.error("OpenAI-Compatible API error: {}", e.getMessage());
            throw new AIProviderUnavailableException(getProviderName(), e.getMessage());
        }
    }

    @Override
    public Flux<String> stream(AiRequest request) {
        if (!isAvailable()) {
            return Flux.error(new AIProviderUnavailableException(getProviderName(),
                    "OpenAI-Compatible endpoint is not reachable at " + baseUrl));
        }

        String model = (request.getRequestedModel() != null && !request.getRequestedModel().isEmpty())
                ? request.getRequestedModel() : defaultModel;

        Map<String, Object> body = buildRequestBody(request, model, true);

        WebClient.Builder builder = webClientBuilder.baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        WebClient webClient = builder.build();

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> !line.trim().isEmpty() && !line.contains("[DONE]"))
                .map(this::extractDeltaToken)
                .filter(token -> !token.isEmpty());
    }

    private String extractDeltaToken(String sseData) {
        try {
            String json = sseData.startsWith("data:") ? sseData.substring(5).trim() : sseData.trim();
            if (json.isEmpty() || json.equals("[DONE]")) return "";
            JsonNode root = objectMapper.readTree(json);
            if (root.has("choices") && root.get("choices").size() > 0) {
                JsonNode delta = root.get("choices").get(0).path("delta");
                if (delta.has("content")) {
                    return delta.get("content").asText();
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    private Map<String, Object> buildRequestBody(AiRequest request, String model, boolean stream) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("stream", stream);

        List<Map<String, String>> messages = new ArrayList<>();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
            messages.add(Map.of("role", "system", "content", request.getSystemPrompt()));
        }

        if (request.getMessages() != null) {
            for (AiRequest.ChatMessage msg : request.getMessages()) {
                messages.add(Map.of("role", msg.getRole().toLowerCase(), "content", msg.getContent()));
            }
        }

        body.put("messages", messages);
        return body;
    }
}
