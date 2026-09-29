package com.nexus.device;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Generic NEXUS Device & HTTP Batch Status Fetcher Service.
 * Executes single or batch HTTP requests across dynamic base URLs, template patterns,
 * and list of device IDs or dynamic parameter values.
 */
public class DeviceStatusFetcher {

    public static final String DEFAULT_BASE_URL = "http://kernel.iviscloud.net:8182/ivis-kernel-server/DeviceCurrentStatusServlet?action=getstatus&deviceId=";

    private final HttpClient httpClient;
    private final String defaultBaseUrlPattern;
    private final Map<String, String> defaultHeaders;
    private final Duration defaultTimeout;

    public DeviceStatusFetcher() {
        this(DEFAULT_BASE_URL);
    }

    public DeviceStatusFetcher(String defaultBaseUrlPattern) {
        this(defaultBaseUrlPattern, Collections.emptyMap(), Duration.ofSeconds(10));
    }

    public DeviceStatusFetcher(String defaultBaseUrlPattern, Map<String, String> defaultHeaders, Duration defaultTimeout) {
        this.defaultBaseUrlPattern = (defaultBaseUrlPattern != null && !defaultBaseUrlPattern.trim().isEmpty())
                ? defaultBaseUrlPattern
                : DEFAULT_BASE_URL;
        this.defaultHeaders = defaultHeaders != null ? new HashMap<>(defaultHeaders) : new HashMap<>();
        this.defaultTimeout = defaultTimeout != null ? defaultTimeout : Duration.ofSeconds(10);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.defaultTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Builds full URL given a pattern and item value/id.
     * If pattern contains "{id}" or "{value}", replaces it. Otherwise appends item.
     */
    public String buildUrl(String pattern, String item) {
        if (pattern == null || pattern.trim().isEmpty()) {
            pattern = defaultBaseUrlPattern;
        }
        String cleanItem = item != null ? item.trim() : "";
        if (pattern.contains("{id}")) {
            return pattern.replace("{id}", cleanItem);
        } else if (pattern.contains("{value}")) {
            return pattern.replace("{value}", cleanItem);
        } else {
            return pattern + cleanItem;
        }
    }

    /**
     * Executes GET request for a single device ID using default base URL pattern.
     */
    public String fetchDeviceStatus(String deviceId) {
        return fetchStatus(defaultBaseUrlPattern, deviceId);
    }

    /**
     * Executes GET request for a specific ID with a custom base URL pattern.
     */
    public String fetchStatus(String baseUrlPattern, String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return "Invalid Device ID: null or empty";
        }
        String targetUrl = buildUrl(baseUrlPattern, deviceId);
        return fetchUrl(targetUrl, "GET", defaultHeaders, null);
    }

    /**
     * Executes HTTP request to any target URL with default headers and GET method.
     */
    public String fetchUrl(String targetUrl) {
        return fetchUrl(targetUrl, "GET", defaultHeaders, null);
    }

    /**
     * Executes generic HTTP request to any target URL with specified method, headers, and body.
     */
    public String fetchUrl(String targetUrl, String method, Map<String, String> headers, String body) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            return "Invalid URL: null or empty";
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl.trim()))
                    .timeout(defaultTimeout)
                    .header("User-Agent", "NEXUS-Generic-Fetcher/2.0");

            if (headers != null) {
                headers.forEach(builder::header);
            }

            String httpMethod = (method != null) ? method.toUpperCase() : "GET";
            switch (httpMethod) {
                case "POST" -> builder.POST(body != null ? HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody());
                case "PUT" -> builder.PUT(body != null ? HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody());
                case "DELETE" -> builder.DELETE();
                default -> builder.GET();
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return "URL [" + targetUrl + "] Status (" + response.statusCode() + " OK): " + response.body();
            } else {
                return "URL [" + targetUrl + "] Failed with HTTP Status: " + response.statusCode() + " Body: " + response.body();
            }
        } catch (Exception e) {
            return "URL [" + targetUrl + "] Exception: " + e.getMessage();
        }
    }

    /**
     * Iterates through a list of IDs sequentially using default URL pattern.
     */
    public List<String> fetchAllDevicesSequential(List<String> deviceIds) {
        return fetchAllSequential(defaultBaseUrlPattern, deviceIds);
    }

    /**
     * Iterates through a list of IDs sequentially using custom URL pattern.
     */
    public List<String> fetchAllSequential(String baseUrlPattern, List<String> deviceIds) {
        System.out.println("--- STARTING SEQUENTIAL FETCH (" + deviceIds.size() + " items) Pattern: " + baseUrlPattern + " ---");
        List<String> results = new ArrayList<>();
        for (String id : deviceIds) {
            String result = fetchStatus(baseUrlPattern, id);
            System.out.println(result);
            results.add(result);
        }
        System.out.println("--- SEQUENTIAL FETCH COMPLETED ---\n");
        return results;
    }

    /**
     * Iterates through a list of IDs in parallel using default URL pattern.
     */
    public List<String> fetchAllDevicesParallel(List<String> deviceIds) {
        return fetchAllParallel(defaultBaseUrlPattern, deviceIds);
    }

    /**
     * Iterates through a list of IDs in parallel using custom URL pattern.
     */
    public List<String> fetchAllParallel(String baseUrlPattern, List<String> deviceIds) {
        System.out.println("--- STARTING PARALLEL FETCH (" + deviceIds.size() + " items) Pattern: " + baseUrlPattern + " ---");
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(Math.max(deviceIds.size(), 1), 20));
        List<String> results = new ArrayList<>();

        try {
            List<CompletableFuture<String>> futures = deviceIds.stream()
                    .map(id -> CompletableFuture.supplyAsync(() -> fetchStatus(baseUrlPattern, id), executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            for (CompletableFuture<String> future : futures) {
                String res = future.get();
                System.out.println(res);
                results.add(res);
            }
        } catch (Exception e) {
            System.err.println("Parallel fetch error: " + e.getMessage());
        } finally {
            executor.shutdown();
        }
        System.out.println("--- PARALLEL FETCH COMPLETED ---\n");
        return results;
    }

    /**
     * Iterates through an arbitrary list of full URLs in parallel.
     */
    public List<String> fetchAllUrlsParallel(List<String> fullUrls) {
        System.out.println("--- STARTING GENERIC PARALLEL URL FETCH (" + fullUrls.size() + " URLs) ---");
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(Math.max(fullUrls.size(), 1), 20));
        List<String> results = new ArrayList<>();

        try {
            List<CompletableFuture<String>> futures = fullUrls.stream()
                    .map(url -> CompletableFuture.supplyAsync(() -> fetchUrl(url), executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            for (CompletableFuture<String> future : futures) {
                String res = future.get();
                System.out.println(res);
                results.add(res);
            }
        } catch (Exception e) {
            System.err.println("Parallel URL fetch error: " + e.getMessage());
        } finally {
            executor.shutdown();
        }
        System.out.println("--- GENERIC PARALLEL URL FETCH COMPLETED ---\n");
        return results;
    }

    public static void main(String[] args) {
        System.out.println("=== NEXUS GENERIC HTTP & DEVICE FETCH DEMONSTRATION ===\n");

        // 1. Default URL Pattern with Device IDs
        DeviceStatusFetcher defaultFetcher = new DeviceStatusFetcher();
        List<String> deviceIds = List.of("DEV_1001", "DEV_1002", "DEV_1003");
        System.out.println("1. Running Default Device Status Queries:");
        defaultFetcher.fetchAllDevicesParallel(deviceIds);

        // 2. Generic Custom Base URL with Query Parameter
        DeviceStatusFetcher genericApiFetcher = new DeviceStatusFetcher("https://jsonplaceholder.typicode.com/posts/{id}");
        List<String> postIds = List.of("1", "2", "3");
        System.out.println("2. Running Custom Generic API Template Queries:");
        genericApiFetcher.fetchAllParallel("https://jsonplaceholder.typicode.com/posts/{id}", postIds);

        // 3. Batch Arbitrary Full URLs Parallel Fetch
        List<String> customUrlList = List.of(
                "https://httpbin.org/get?sensor=zone1",
                "https://httpbin.org/get?sensor=zone2",
                "https://httpbin.org/get?sensor=zone3"
        );
        System.out.println("3. Running Batch Arbitrary Full URLs Parallel Fetch:");
        defaultFetcher.fetchAllUrlsParallel(customUrlList);
    }
}
