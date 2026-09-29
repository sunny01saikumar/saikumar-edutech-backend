package com.nexus.ai.tools;

import com.nexus.ai.domain.ToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class DeployTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(DeployTool.class);

    @Override
    public String getName() {
        return "deploy";
    }

    @Override
    public String getDescription() {
        return "Triggers automated application deployments, production builds, Git push releases, or deployment webhooks.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "target", Map.of("type", "string", "description", "Deployment target action: 'git_push', 'maven_build', 'docker_build', or 'deploy_webhook'"),
                        "commitMessage", Map.of("type", "string", "description", "Optional Git commit message for git_push deployment"),
                        "webhookUrl", Map.of("type", "string", "description", "Optional HTTP POST deployment webhook URL")
                ),
                "required", List.of("target")
        );
    }

    @Override
    public ToolCall execute(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        String target = String.valueOf(arguments.get("target")).toLowerCase();
        String commitMessage = arguments.containsKey("commitMessage") ? String.valueOf(arguments.get("commitMessage")) : "deploy: automated build release by NEXUS AI";
        String webhookUrl = arguments.containsKey("webhookUrl") ? String.valueOf(arguments.get("webhookUrl")) : "";

        try {
            switch (target) {
                case "git_push":
                    return executeGitPushDeploy(arguments, commitMessage, start);
                case "maven_build":
                    return executeMavenBuild(arguments, start);
                case "docker_build":
                    return executeDockerBuild(arguments, start);
                case "deploy_webhook":
                    return executeWebhookDeploy(arguments, webhookUrl, start);
                default:
                    return executeGitPushDeploy(arguments, commitMessage, start);
            }
        } catch (Exception e) {
            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result("Deployment error: " + e.getMessage())
                    .success(false)
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    private ToolCall executeGitPushDeploy(Map<String, Object> arguments, String commitMessage, long start) throws Exception {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String gitCmd = isWindows
                ? "git add . && git commit -m \"" + commitMessage.replaceAll("\"", "'") + "\" && git push origin main"
                : "git add . && git commit -m '" + commitMessage.replaceAll("'", "") + "' && git push origin main";

        String output = runCommand(gitCmd);
        boolean success = !output.contains("fatal:") && !output.contains("error:");

        return ToolCall.builder()
                .id(UUID.randomUUID().toString())
                .toolName(getName())
                .arguments(arguments)
                .result("--- GIT PUSH DEPLOYMENT RESULT ---\n" + output)
                .success(success)
                .executionTimeMs(System.currentTimeMillis() - start)
                .build();
    }

    private ToolCall executeMavenBuild(Map<String, Object> arguments, long start) throws Exception {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String cmd = isWindows ? "mvnw.cmd clean package -DskipTests" : "./mvnw clean package -DskipTests";
        String output = runCommand(cmd);
        boolean success = output.contains("BUILD SUCCESS");

        return ToolCall.builder()
                .id(UUID.randomUUID().toString())
                .toolName(getName())
                .arguments(arguments)
                .result("--- MAVEN PACKAGE BUILD RESULT ---\n" + output)
                .success(success)
                .executionTimeMs(System.currentTimeMillis() - start)
                .build();
    }

    private ToolCall executeDockerBuild(Map<String, Object> arguments, long start) throws Exception {
        String cmd = "docker build -t nexus-ai:latest .";
        String output = runCommand(cmd);
        boolean success = output.contains("naming to docker.io") || output.contains("Successfully tagged");

        return ToolCall.builder()
                .id(UUID.randomUUID().toString())
                .toolName(getName())
                .arguments(arguments)
                .result("--- DOCKER BUILD RESULT ---\n" + output)
                .success(success)
                .executionTimeMs(System.currentTimeMillis() - start)
                .build();
    }

    private ToolCall executeWebhookDeploy(Map<String, Object> arguments, String url, long start) {
        if (url == null || url.trim().isEmpty()) {
            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result("Deployment webhook URL is missing.")
                    .success(false)
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .build();
        }

        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.ofString("{\"source\":\"NEXUS_AI_ENGINE\",\"event\":\"deploy\"}"))
                    .header("Content-Type", "application/json")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;

            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result("Webhook Deployment HTTP Status: " + response.statusCode() + "\nBody: " + response.body())
                    .success(success)
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result("Webhook deployment failed: " + e.getMessage())
                    .success(false)
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    private String runCommand(String command) throws Exception {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        ProcessBuilder pb = new ProcessBuilder();
        if (isWindows) {
            pb.command("cmd.exe", "/c", command);
        } else {
            pb.command("sh", "-c", command);
        }

        pb.redirectErrorStream(true);
        Process p = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                if (output.length() > 15000) {
                    output.append("\n...[output truncated]");
                    break;
                }
            }
        }

        p.waitFor(60, TimeUnit.SECONDS);
        return output.toString();
    }
}
