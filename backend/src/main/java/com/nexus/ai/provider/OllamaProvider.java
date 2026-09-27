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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;

@Component
public class OllamaProvider implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaProvider.class);

    @Value("${nexus.ai.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${nexus.ai.ollama.model:llama3}")
    private String defaultModel;

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public OllamaProvider(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "ollama";
    }

    @Override
    public boolean isAvailable() {
        try {
            WebClient webClient = webClientBuilder.baseUrl(baseUrl)
                    .defaultHeader("bypass-tunnel-reminder", "true")
                    .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                    .build();
            String res = webClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();
            return res != null && res.contains("models");
        } catch (Exception e) {
            log.warn("Ollama availability probe failed at {}: {}", baseUrl, e.getMessage());
            return false;
        }
    }

    @Override
    public AiResponse generate(AiRequest request) {
        long start = System.currentTimeMillis();
        String model = (request.getRequestedModel() != null && !request.getRequestedModel().isEmpty())
                ? request.getRequestedModel() : defaultModel;

        Map<String, Object> body = buildRequestBody(request, model, false);

        WebClient webClient = webClientBuilder.baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("bypass-tunnel-reminder", "true")
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                .build();

        try {
            String jsonResponse = webClient.post()
                    .uri("/api/chat")
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(status -> status.isError(), clientResponse ->
                        clientResponse.bodyToMono(String.class).map(err ->
                            new AIProviderUnavailableException(getProviderName(), "Ollama error: " + err)
                        )
                    )
                    .bodyToMono(String.class)
                    .block();

            long latency = System.currentTimeMillis() - start;
            JsonNode root = objectMapper.readTree(jsonResponse);

            String content = root.path("message").path("content").asText("");
            int evalCount = root.path("eval_count").asInt(0);
            int promptEvalCount = root.path("prompt_eval_count").asInt(0);

            return AiResponse.builder()
                    .id(UUID.randomUUID().toString())
                    .content(content)
                    .provider(getProviderName())
                    .model(model)
                    .usage(new AiUsage(promptEvalCount, evalCount, promptEvalCount + evalCount))
                    .latencyMs(latency)
                    .finishReason(root.path("done_reason").asText("stop"))
                    .build();

        } catch (Exception e) {
            log.error("Ollama execution error: {}", e.getMessage());
            throw new AIProviderUnavailableException(getProviderName(), e.getMessage());
        }
    }

    @Override
    public Flux<String> stream(AiRequest request) {
        String model = (request.getRequestedModel() != null && !request.getRequestedModel().isEmpty())
                ? request.getRequestedModel() : defaultModel;

        Map<String, Object> body = buildRequestBody(request, model, true);

        WebClient webClient = webClientBuilder.baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("bypass-tunnel-reminder", "true")
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                .build();

        return webClient.post()
                .uri("/api/chat")
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.isError(), clientResponse ->
                    clientResponse.bodyToMono(String.class).map(errBody ->
                        new AIProviderUnavailableException(getProviderName(), "Ollama connection error: " + errBody)
                    )
                )
                .bodyToFlux(String.class)
                .flatMap(chunk -> Flux.fromArray(chunk.split("\n")))
                .map(this::extractDeltaToken)
                .filter(token -> !token.isEmpty())
                .onErrorResume(e -> Flux.error(new AIProviderUnavailableException(getProviderName(), "NEXUS Engine connection failed: " + e.getMessage())));
    }

    private String extractDeltaToken(String line) {
        try {
            if (line == null || line.trim().isEmpty()) return "";
            JsonNode root = objectMapper.readTree(line.trim());
            if (root.has("error")) {
                throw new RuntimeException(root.get("error").asText());
            }
            if (root.has("message") && root.get("message").has("content")) {
                return root.get("message").get("content").asText();
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
