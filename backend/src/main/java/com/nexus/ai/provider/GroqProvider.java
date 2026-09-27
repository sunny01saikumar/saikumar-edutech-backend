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
public class GroqProvider implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger(GroqProvider.class);

    @Value("${nexus.ai.groq.api-key:}")
    private String envApiKey;

    @Value("${nexus.ai.gemini.api-key:}")
    private String geminiEnvApiKey;

    @Value("${nexus.ai.openai.api-key:}")
    private String openaiEnvApiKey;

    @Value("${nexus.ai.groq.model:llama-3.3-70b-versatile}")
    private String defaultModel;

    @Value("${nexus.ai.groq.base-url:https://api.groq.com/openai/v1}")
    private String baseUrl;

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public GroqProvider(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "groq";
    }

    @Override
    public boolean isAvailable() {
        String key = getSystemGroqKey();
        return !key.isEmpty();
    }

    private String sanitizeKey(String raw) {
        if (raw == null) return "";
        return raw.trim().replaceAll("[\\r\\n\\t\"]", "").replaceAll("^['\"]|['\"]$", "").trim();
    }

    private String getSystemGroqKey() {
        String key = sanitizeKey(envApiKey);
        if (key.startsWith("gsk_")) return key;

        String geminiKey = sanitizeKey(geminiEnvApiKey);
        if (geminiKey.startsWith("gsk_")) return geminiKey;

        String openaiKey = sanitizeKey(openaiEnvApiKey);
        if (openaiKey.startsWith("gsk_")) return openaiKey;

        if (!key.isEmpty() && !key.equalsIgnoreCase("null") && !key.equalsIgnoreCase("your_groq_api_key")) {
            return key;
        }
        return "";
    }

    private String getEffectiveKey(AiRequest request) {
        if (request != null && request.getCustomApiKey() != null && !request.getCustomApiKey().trim().isEmpty()) {
            return sanitizeKey(request.getCustomApiKey());
        }
        return getSystemGroqKey();
    }

    @Override
    public AiResponse generate(AiRequest request) {
        String apiKey = getEffectiveKey(request);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new AIProviderUnavailableException(getProviderName(), "Groq API Key is missing or empty. Please enter your key in settings or .env.");
        }

        long start = System.currentTimeMillis();
        String model = (request.getRequestedModel() != null && !request.getRequestedModel().isEmpty())
                ? request.getRequestedModel() : defaultModel;

        Map<String, Object> body = buildRequestBody(request, model, false);

        WebClient webClient = webClientBuilder.baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

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

            int promptTokens = 0, completionTokens = 0, totalTokens = 0;
            if (root.has("usage")) {
                JsonNode usage = root.get("usage");
                promptTokens = usage.path("prompt_tokens").asInt(0);
                completionTokens = usage.path("completion_tokens").asInt(0);
                totalTokens = usage.path("total_tokens").asInt(0);
            }

            return AiResponse.builder()
                    .id(root.path("id").asText(UUID.randomUUID().toString()))
                    .content(content)
                    .provider(getProviderName())
                    .model(model)
                    .usage(new AiUsage(promptTokens, completionTokens, totalTokens))
                    .latencyMs(latency)
                    .finishReason(root.path("choices").path(0).path("finish_reason").asText("stop"))
                    .build();

        } catch (WebClientResponseException ex) {
            log.error("Groq API error (HTTP {}): {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            String detail = parseErrorMessage(ex);
            throw new AIProviderUnavailableException(getProviderName(), "Groq API Error (" + ex.getStatusCode().value() + "): " + detail);
        } catch (Exception e) {
            log.error("Groq API execution error: {}", e.getMessage());
            throw new AIProviderUnavailableException(getProviderName(), e.getMessage());
        }
    }

    @Override
    public Flux<String> stream(AiRequest request) {
        String apiKey = getEffectiveKey(request);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return Flux.error(new AIProviderUnavailableException(getProviderName(), "Groq API Key is missing. Please enter your key in settings or .env."));
        }

        String model = (request.getRequestedModel() != null && !request.getRequestedModel().isEmpty())
                ? request.getRequestedModel() : defaultModel;

        Map<String, Object> body = buildRequestBody(request, model, true);

        WebClient webClient = webClientBuilder.baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> !line.trim().isEmpty() && !line.contains("[DONE]"))
                .map(this::extractDeltaToken)
                .filter(token -> !token.isEmpty())
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.error("Groq API Streaming error (HTTP {}): {}", ex.getStatusCode(), ex.getResponseBodyAsString());
                    String detail = parseErrorMessage(ex);
                    return Flux.error(new AIProviderUnavailableException(getProviderName(), "Groq API Error (" + ex.getStatusCode().value() + "): " + detail));
                });
    }

    private String parseErrorMessage(WebClientResponseException ex) {
        try {
            String body = ex.getResponseBodyAsString();
            if (body != null && !body.trim().isEmpty()) {
                JsonNode root = objectMapper.readTree(body);
                String msg = root.path("error").path("message").asText("");
                if (!msg.isEmpty()) return msg;
            }
        } catch (Exception ignored) {}
        return ex.getMessage();
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
        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            body.put("max_tokens", request.getMaxTokens());
        }
        return body;
    }
}
