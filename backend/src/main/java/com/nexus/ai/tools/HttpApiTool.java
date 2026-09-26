package com.nexus.ai.tools;

import com.nexus.ai.domain.ToolCall;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

@Component
public class HttpApiTool implements Tool {

    private final WebClient webClient;

    public HttpApiTool(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    @Override
    public String getName() {
        return "http_api";
    }

    @Override
    public String getDescription() {
        return "Executes HTTP GET requests to external APIs or microservices.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "url", Map.of("type", "string", "description", "Full URL to perform HTTP GET request")
                ),
                "required", List.of("url")
        );
    }

    @Override
    public ToolCall execute(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        String url = String.valueOf(arguments.get("url"));

        try {
            String res = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (res != null && res.length() > 5000) {
                res = res.substring(0, 5000) + "\n...[truncated at 5,000 characters]";
            }

            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result(res)
                    .success(true)
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .build();

        } catch (Exception e) {
            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result("HTTP API error: " + e.getMessage())
                    .success(false)
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .build();
        }
    }
}
