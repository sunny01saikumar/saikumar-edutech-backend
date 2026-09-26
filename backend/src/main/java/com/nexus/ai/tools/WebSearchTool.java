package com.nexus.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.domain.ToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Component
public class WebSearchTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public WebSearchTool(WebClient.Builder builder, ObjectMapper objectMapper) {
        this.webClient = builder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "web_search";
    }

    @Override
    public String getDescription() {
        return "Performs live web search for current news, facts, articles, documentation, or real-time topics.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "The search query to look up on the web")
                ),
                "required", List.of("query")
        );
    }

    @Override
    public ToolCall execute(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        String query = String.valueOf(arguments.get("query"));

        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String wikipediaUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srsearch=" + encoded;

            String jsonResponse = webClient.get()
                    .uri(wikipediaUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode search = root.path("query").path("search");

            StringBuilder sb = new StringBuilder();
            sb.append("Web Search Results for: ").append(query).append("\n\n");

            if (search.isArray() && search.size() > 0) {
                int count = 0;
                for (JsonNode item : search) {
                    if (count++ >= 5) break;
                    String title = item.path("title").asText();
                    String snippet = item.path("snippet").asText().replaceAll("<[^>]*>", "");
                    sb.append("[").append(count).append("] ").append(title).append("\n");
                    sb.append("    Snippet: ").append(snippet).append("\n\n");
                }
            } else {
                sb.append("No direct web search results found for query: ").append(query);
            }

            long latency = System.currentTimeMillis() - start;
            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result(sb.toString())
                    .success(true)
                    .executionTimeMs(latency)
                    .build();

        } catch (Exception e) {
            log.error("Web search execution failed: {}", e.getMessage());
            long latency = System.currentTimeMillis() - start;
            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result("Web Search error: " + e.getMessage())
                    .success(false)
                    .executionTimeMs(latency)
                    .build();
        }
    }
}
