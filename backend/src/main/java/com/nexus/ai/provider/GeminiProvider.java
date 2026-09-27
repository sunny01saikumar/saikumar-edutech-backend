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
        String key = sanitizeKey(envApiKey);
        return !key.isEmpty() && !key.equalsIgnoreCase("your_gemini_api_key") && !key.equalsIgnoreCase("null");
    }

    private String sanitizeKey(String raw) {
        if (raw == null) return "";
        return raw.trim().replaceAll("[\\r\\n\\t\"]", "").replaceAll("^['\"]|['\"]$", "").trim();
    }

    private String getEffectiveKey(AiRequest request) {
        if (request != null && request.getCustomApiKey() != null && !request.getCustomApiKey().trim().isEmpty()) {
            return sanitizeKey(request.getCustomApiKey());
        }
        return sanitizeKey(envApiKey);
    }

    private List<String> discoverAvailableModels(WebClient webClient, String apiKey, String preferredModel) {
        List<String> discovered = new ArrayList<>();

        try {
            String json = webClient.get()
                    .uri("models?key=" + apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (json != null) {
                JsonNode root = objectMapper.readTree(json);
                if (root.has("models") && root.get("models").isArray()) {
                    for (JsonNode m : root.get("models")) {
                        String name = m.path("name").asText("");
                        if (name.startsWith("models/")) {
                            name = name.substring(7);
                        }
                        String lower = name.toLowerCase();
                        if (lower.contains("tts") || lower.contains("embedding") || lower.contains("imagen")
                                || lower.contains("audio") || lower.contains("aqa") || lower.contains("bison")
                                || lower.contains("gecko") || lower.contains("realtime")) {
                            continue;
                        }
                        JsonNode methods = m.path("supportedGenerationMethods");
                        boolean supportsGen = false;
                        if (methods.isArray()) {
                            for (JsonNode meth : methods) {
                                String mStr = meth.asText();
                                if ("generateContent".equals(mStr) || "streamGenerateContent".equals(mStr)) {
                                    supportsGen = true;
                                    break;
                                }
                            }
                        }
                        if (supportsGen && !name.isEmpty() && !discovered.contains(name)) {
                            discovered.add(name);
                        }
                    }
                }
            }
        } catch (WebClientResponseException ex) {
            log.warn("Gemini API key verification warning (HTTP {}): {}", ex.getStatusCode(), ex.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("Failed to dynamically fetch Gemini models: {}", e.getMessage());
        }

        List<String> prioritized = new ArrayList<>();
        if (preferredModel != null && !preferredModel.trim().isEmpty()
                && !preferredModel.equalsIgnoreCase("default")
                && !preferredModel.equalsIgnoreCase("default model")) {
            prioritized.add(preferredModel.trim().replace("models/", ""));
        }

        List<String> flagshipDefaults = List.of(
            "gemini-1.5-flash",
            "gemini-2.0-flash",
            "gemini-2.5-flash",
            "gemini-1.5-pro",
            "gemini-2.0-flash-lite",
            "gemini-1.5-flash-8b",
            "gemini-pro"
        );
        for (String f : flagshipDefaults) {
            if (discovered.contains(f) && !prioritized.contains(f)) {
                prioritized.add(f);
            }
        }
        for (String d : discovered) {
            if (!prioritized.contains(d)) {
                prioritized.add(d);
            }
        }
        for (String f : flagshipDefaults) {
            if (!prioritized.contains(f)) {
                prioritized.add(f);
            }
        }
        return prioritized;
    }

    private String parseGoogleErrorMessage(WebClientResponseException ex) {
        try {
            String body = ex.getResponseBodyAsString();
            if (body != null && !body.trim().isEmpty()) {
                JsonNode root = objectMapper.readTree(body);
                String msg = root.path("error").path("message").asText("");
                if (!msg.isEmpty()) {
                    return msg;
                }
            }
        } catch (Exception ignored) {}
        return ex.getMessage();
    }

    @Override
    public AiResponse generate(AiRequest request) {
        String apiKey = getEffectiveKey(request);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new AIProviderUnavailableException(getProviderName(), "Google Gemini API Key is missing or empty. Please enter your key in settings or .env.");
        }

        long start = System.currentTimeMillis();
        Map<String, Object> body = buildRequestBody(request);

        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        WebClient webClient = webClientBuilder.baseUrl(normalizedBaseUrl)
                .defaultHeader("x-goog-api-key", apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        List<String> candidates = discoverAvailableModels(webClient, apiKey, request.getRequestedModel());
        for (String model : candidates) {
            try {
                String jsonResponse = webClient.post()
                        .uri("models/" + model + ":generateContent?key=" + apiKey)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

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

            } catch (WebClientResponseException ex) {
                log.warn("Gemini model [{}] returned HTTP {}: {}", model, ex.getStatusCode(), ex.getResponseBodyAsString());
                if (ex.getStatusCode().value() == 400 || ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                    String detail = parseGoogleErrorMessage(ex);
                    throw new AIProviderUnavailableException(getProviderName(), "Google Gemini Error (" + ex.getStatusCode().value() + "): " + detail);
                }
            } catch (Exception e) {
                log.error("Gemini execution error on model [{}]: {}", model, e.getMessage());
                throw new AIProviderUnavailableException(getProviderName(), e.getMessage());
            }
        }

        throw new AIProviderUnavailableException(getProviderName(), "No valid Gemini models found for API key. Please check your key in settings.");
    }

    @Override
    public Flux<String> stream(AiRequest request) {
        String apiKey = getEffectiveKey(request);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return Flux.error(new AIProviderUnavailableException(getProviderName(), "Google Gemini API Key is missing. Please enter your key in settings or .env."));
        }

        Map<String, Object> body = buildRequestBody(request);

        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        WebClient webClient = webClientBuilder.baseUrl(normalizedBaseUrl)
                .defaultHeader("x-goog-api-key", apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        List<String> candidates;
        try {
            candidates = discoverAvailableModels(webClient, apiKey, request.getRequestedModel());
        } catch (AIProviderUnavailableException ex) {
            return Flux.error(ex);
        }
        return streamWithCandidates(webClient, candidates, 0, apiKey, body);
    }

    private Flux<String> streamWithCandidates(WebClient webClient, List<String> candidates, int index, String apiKey, Map<String, Object> body) {
        if (index >= candidates.size()) {
            return Flux.error(new AIProviderUnavailableException(getProviderName(), "No valid Gemini models found for API key. Please check your key in settings."));
        }

        String model = candidates.get(index);
        return webClient.post()
                .uri("models/" + model + ":streamGenerateContent?alt=sse&key=" + apiKey)
                .bodyValue(body)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .map(this::extractDeltaToken)
                .filter(token -> !token.isEmpty())
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.warn("Gemini model [{}] failed with HTTP {}: {}", model, ex.getStatusCode(), ex.getResponseBodyAsString());
                    if (ex.getStatusCode().value() == 400 || ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                        String detail = parseGoogleErrorMessage(ex);
                        return Flux.error(new AIProviderUnavailableException(getProviderName(), "Google Gemini Error (" + ex.getStatusCode().value() + "): " + detail));
                    }
                    return streamWithCandidates(webClient, candidates, index + 1, apiKey, body);
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
