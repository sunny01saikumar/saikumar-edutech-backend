package com.nexus.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.domain.ToolCall;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Component
public class WeatherTool implements Tool {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public WeatherTool(WebClient.Builder builder, ObjectMapper objectMapper) {
        this.webClient = builder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "weather";
    }

    @Override
    public String getDescription() {
        return "Fetches real-time weather information and forecast for a given city name.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "city", Map.of("type", "string", "description", "City name (e.g. 'London', 'Tokyo', 'New York')")
                ),
                "required", List.of("city")
        );
    }

    @Override
    public ToolCall execute(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        String city = String.valueOf(arguments.get("city"));

        try {
            String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=" + URLEncoder.encode(city, StandardCharsets.UTF_8) + "&count=1";
            String geoJson = webClient.get().uri(geoUrl).retrieve().bodyToMono(String.class).timeout(Duration.ofSeconds(4)).block();

            JsonNode geoNode = objectMapper.readTree(geoJson);
            JsonNode results = geoNode.path("results");
            if (!results.isArray() || results.size() == 0) {
                return ToolCall.builder()
                        .id(UUID.randomUUID().toString())
                        .toolName(getName())
                        .arguments(arguments)
                        .result("Could not locate city: " + city)
                        .success(false)
                        .executionTimeMs(System.currentTimeMillis() - start)
                        .build();
            }

            double lat = results.get(0).path("latitude").asDouble();
            double lon = results.get(0).path("longitude").asDouble();
            String name = results.get(0).path("name").asText();
            String country = results.get(0).path("country").asText("");

            String weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current_weather=true";
            String weatherJson = webClient.get().uri(weatherUrl).retrieve().bodyToMono(String.class).timeout(Duration.ofSeconds(4)).block();

            JsonNode weatherNode = objectMapper.readTree(weatherJson).path("current_weather");
            double temp = weatherNode.path("temperature").asDouble();
            double windspeed = weatherNode.path("windspeed").asDouble();

            String result = String.format("Current weather in %s, %s:\nTemperature: %.1f°C\nWind speed: %.1f km/h", name, country, temp, windspeed);

            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result(result)
                    .success(true)
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .build();

        } catch (Exception e) {
            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result("Weather API error: " + e.getMessage())
                    .success(false)
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .build();
        }
    }
}
