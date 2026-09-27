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

    private String extractCleanKeywords(String rawQuery) {
        if (rawQuery == null) return "";
        String cleaned = rawQuery.trim()
                .replaceAll("(?i)^(what is the special of|what is the specialty of|what is|tell me about|explain|where is|who is|how does|what are the|search web for|search for|about|special of|specialty of)\\s+", "")
                .replaceAll("[?!=.,\"]", "")
                .trim();
        return cleaned.isEmpty() ? rawQuery.trim() : cleaned;
    }

    @Override
    public ToolCall execute(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        String rawQuery = String.valueOf(arguments.get("query"));
        String cleanQuery = extractCleanKeywords(rawQuery);

        try {
            String encodedClean = URLEncoder.encode(cleanQuery, StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder();
            sb.append("Live Search Results for: ").append(cleanQuery).append("\n\n");

            // 1. DuckDuckGo Instant Answer
            try {
                String ddgUrl = "https://api.duckduckgo.com/?q=" + encodedClean + "&format=json&no_html=1&skip_disambig=1";
                String ddgJson = webClient.get()
                        .uri(ddgUrl)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(4))
                        .block();
                if (ddgJson != null) {
                    JsonNode ddgRoot = objectMapper.readTree(ddgJson);
                    String abstractText = ddgRoot.path("AbstractText").asText("");
                    if (!abstractText.isEmpty()) {
                        sb.append("Summary: ").append(abstractText).append("\n\n");
                    }
                }
            } catch (Exception e) {
                log.warn("DuckDuckGo search warning: {}", e.getMessage());
            }

            // 2. Wikipedia Search (Clean Keyword Search)
            boolean wikiFound = false;
            try {
                String wikipediaUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srsearch=" + encodedClean;
                String wikiJson = webClient.get()
                        .uri(wikipediaUrl)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(4))
                        .block();
                if (wikiJson != null) {
                    JsonNode wikiRoot = objectMapper.readTree(wikiJson);
                    JsonNode search = wikiRoot.path("query").path("search");
                    if (search.isArray() && search.size() > 0) {
                        wikiFound = true;
                        int count = 0;
                        for (JsonNode item : search) {
                            if (count++ >= 4) break;
                            String title = item.path("title").asText();
                            String snippet = item.path("snippet").asText().replaceAll("<[^>]*>", "");
                            sb.append("[").append(count).append("] ").append(title).append("\n");
                            sb.append("    ").append(snippet).append("\n\n");
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Wikipedia search warning: {}", e.getMessage());
            }

            // Fallback Search if clean keyword returned nothing
            if (!wikiFound && !cleanQuery.equalsIgnoreCase(rawQuery)) {
                try {
                    String encodedRaw = URLEncoder.encode(rawQuery, StandardCharsets.UTF_8);
                    String fallbackUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srsearch=" + encodedRaw;
                    String wikiJson = webClient.get()
                            .uri(fallbackUrl)
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(Duration.ofSeconds(4))
                            .block();
                    if (wikiJson != null) {
                        JsonNode wikiRoot = objectMapper.readTree(wikiJson);
                        JsonNode search = wikiRoot.path("query").path("search");
                        if (search.isArray() && search.size() > 0) {
                            int count = 0;
                            for (JsonNode item : search) {
                                if (count++ >= 3) break;
                                String title = item.path("title").asText();
                                String snippet = item.path("snippet").asText().replaceAll("<[^>]*>", "");
                                sb.append("[").append(count).append("] ").append(title).append("\n");
                                sb.append("    ").append(snippet).append("\n\n");
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (sb.length() < 35) {
                sb.append("No direct web search results found for query: ").append(cleanQuery);
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
