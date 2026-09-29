package com.nexus.ai.tools;

import com.nexus.ai.domain.ToolCall;
import com.nexus.device.DeviceStatusFetcher;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

@Component
public class HttpApiTool implements Tool {

    private final WebClient webClient;
    private final DeviceStatusFetcher genericFetcher;

    public HttpApiTool(WebClient.Builder builder) {
        this.webClient = builder.build();
        this.genericFetcher = new DeviceStatusFetcher();
    }

    @Override
    public String getName() {
        return "http_api";
    }

    @Override
    public String getDescription() {
        return "Executes single or batch HTTP requests across dynamic base URLs, query templates, or full URL lists.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "url", Map.of("type", "string", "description", "Single full URL to perform HTTP request"),
                        "urls", Map.of("type", "array", "items", Map.of("type", "string"), "description", "List of full URLs for batch requests"),
                        "baseUrl", Map.of("type", "string", "description", "Base URL pattern or template (e.g., 'http://api.org/get?id=' or 'https://api.org/items/{id}')"),
                        "ids", Map.of("type", "array", "items", Map.of("type", "string"), "description", "List of IDs or parameters to format into baseUrl"),
                        "method", Map.of("type", "string", "description", "HTTP method: GET, POST, PUT, DELETE (Default: GET)")
                )
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolCall execute(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();

        try {
            String method = arguments.containsKey("method") ? String.valueOf(arguments.get("method")) : "GET";

            // Case 1: Base URL pattern with dynamic IDs list
            if (arguments.containsKey("baseUrl") && arguments.containsKey("ids")) {
                String baseUrl = String.valueOf(arguments.get("baseUrl"));
                List<String> ids = (List<String>) arguments.get("ids");
                List<String> results = genericFetcher.fetchAllParallel(baseUrl, ids);
                String resultStr = String.join("\n", results);

                return ToolCall.builder()
                        .id(UUID.randomUUID().toString())
                        .toolName(getName())
                        .arguments(arguments)
                        .result(resultStr)
                        .success(true)
                        .executionTimeMs(System.currentTimeMillis() - start)
                        .build();
            }

            // Case 2: Batch list of URLs
            if (arguments.containsKey("urls")) {
                List<String> urls = (List<String>) arguments.get("urls");
                List<String> results = genericFetcher.fetchAllUrlsParallel(urls);
                String resultStr = String.join("\n", results);

                return ToolCall.builder()
                        .id(UUID.randomUUID().toString())
                        .toolName(getName())
                        .arguments(arguments)
                        .result(resultStr)
                        .success(true)
                        .executionTimeMs(System.currentTimeMillis() - start)
                        .build();
            }

            // Case 3: Single URL GET/HTTP Request
            String url = String.valueOf(arguments.get("url"));
            if (url == null || url.trim().isEmpty() || "null".equals(url)) {
                throw new IllegalArgumentException("No URL, URLs list, or baseUrl+ids provided.");
            }

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
