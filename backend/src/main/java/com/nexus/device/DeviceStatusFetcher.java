package com.nexus.device;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NEXUS Device Current Status Fetcher Service.
 * Executes HTTP GET status requests for each device ID in an ArrayList.
 */
public class DeviceStatusFetcher {

    private static final String BASE_URL = "http://kernel.iviscloud.net:8182/ivis-kernel-server/DeviceCurrentStatusServlet?action=getstatus&deviceId=";

    private final HttpClient httpClient;

    public DeviceStatusFetcher() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Executes GET request for a single device ID.
     */
    public String fetchDeviceStatus(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return "Invalid Device ID: null or empty";
        }
        String fullUrl = BASE_URL + deviceId.trim();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) NEXUS-Device-Status/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return "Device [" + deviceId + "] Status (200 OK): " + response.body();
            } else {
                return "Device [" + deviceId + "] Failed with HTTP Status: " + response.statusCode();
            }
        } catch (Exception e) {
            return "Device [" + deviceId + "] Exception: " + e.getMessage();
        }
    }

    /**
     * Iterates through an ArrayList of device IDs sequentially.
     */
    public void fetchAllDevicesSequential(List<String> deviceIds) {
        System.out.println("--- STARTING SEQUENTIAL DEVICE STATUS FETCH (" + deviceIds.size() + " devices) ---");
        for (String deviceId : deviceIds) {
            String result = fetchDeviceStatus(deviceId);
            System.out.println(result);
        }
        System.out.println("--- SEQUENTIAL FETCH COMPLETED ---\n");
    }

    /**
     * Iterates through an ArrayList of device IDs in parallel for high speed.
     */
    public void fetchAllDevicesParallel(List<String> deviceIds) {
        System.out.println("--- STARTING PARALLEL DEVICE STATUS FETCH (" + deviceIds.size() + " devices) ---");
        ExecutorService executor = Executors.newFixedThreadPool(10);

        try {
            List<CompletableFuture<String>> futures = deviceIds.stream()
                    .map(deviceId -> CompletableFuture.supplyAsync(() -> fetchDeviceStatus(deviceId), executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            for (CompletableFuture<String> future : futures) {
                System.out.println(future.get());
            }
        } catch (Exception e) {
            System.err.println("Parallel fetch error: " + e.getMessage());
        } finally {
            executor.shutdown();
        }
        System.out.println("--- PARALLEL FETCH COMPLETED ---\n");
    }

    public static void main(String[] args) {
        DeviceStatusFetcher fetcher = new DeviceStatusFetcher();

        // Sample ArrayList of Device IDs
        List<String> deviceList = new ArrayList<>();
        deviceList.add("DEV_1001");
        deviceList.add("DEV_1002");
        deviceList.add("DEV_1003");
        deviceList.add("DEV_1004");
        deviceList.add("DEV_1005");

        // 1. Run sequentially
        fetcher.fetchAllDevicesSequential(deviceList);

        // 2. Run in parallel (Faster for large device lists)
        fetcher.fetchAllDevicesParallel(deviceList);
    }
}
