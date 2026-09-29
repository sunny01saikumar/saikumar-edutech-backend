package com.nexus.ai.tools;

import com.nexus.ai.domain.ToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Component
public class FileReaderTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(FileReaderTool.class);

    @Override
    public String getName() {
        return "file_reader";
    }

    @Override
    public String getDescription() {
        return "Reads content from a local file path within the project workspace.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "filePath", Map.of("type", "string", "description", "Relative or absolute path of file to read")
                ),
                "required", List.of("filePath")
        );
    }

    @Override
    public ToolCall execute(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();

        String pathStr = null;
        if (arguments.containsKey("filePath") && arguments.get("filePath") != null && !String.valueOf(arguments.get("filePath")).equalsIgnoreCase("null")) {
            pathStr = String.valueOf(arguments.get("filePath"));
        } else if (arguments.containsKey("path") && arguments.get("path") != null && !String.valueOf(arguments.get("path")).equalsIgnoreCase("null")) {
            pathStr = String.valueOf(arguments.get("path"));
        } else if (arguments.containsKey("targetFile") && arguments.get("targetFile") != null && !String.valueOf(arguments.get("targetFile")).equalsIgnoreCase("null")) {
            pathStr = String.valueOf(arguments.get("targetFile"));
        } else if (arguments.containsKey("file") && arguments.get("file") != null && !String.valueOf(arguments.get("file")).equalsIgnoreCase("null")) {
            pathStr = String.valueOf(arguments.get("file"));
        }

        if (pathStr == null || pathStr.trim().isEmpty() || pathStr.equalsIgnoreCase("null")) {
            pathStr = "./"; // Default to root project workspace
        }

        try {
            Path path = Paths.get(pathStr);

            // Handle Windows absolute path translation for Linux hosting containers
            if (!Files.exists(path) && (pathStr.contains(":\\") || pathStr.contains(":/"))) {
                String fileName = Paths.get(pathStr).getFileName() != null ? Paths.get(pathStr).getFileName().toString() : "";
                Path relativePath = Paths.get("./" + fileName);
                if (Files.exists(relativePath)) {
                    path = relativePath;
                } else {
                    path = Paths.get("./");
                }
            }

            if (!Files.exists(path)) {
                Path relativeToRoot = Paths.get("./").resolve(pathStr);
                if (Files.exists(relativeToRoot)) {
                    path = relativeToRoot;
                } else {
                    // Fallback: Check if file exists inside JAR classpath
                    String resourcePath = pathStr;
                    if (resourcePath.contains("resources/")) {
                        resourcePath = resourcePath.substring(resourcePath.indexOf("resources/") + 10);
                    }
                    if (resourcePath.startsWith("/")) resourcePath = resourcePath.substring(1);

                    try (var is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                        if (is != null) {
                            String content = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                            if (content.length() > 10000) {
                                content = content.substring(0, 10000) + "\n...[truncated to 10,000 characters]";
                            }
                            return ToolCall.builder()
                                    .id(UUID.randomUUID().toString())
                                    .toolName(getName())
                                    .arguments(arguments)
                                    .result("--- READ FROM JAR CLASSPATH [" + resourcePath + "] ---\n" + content)
                                    .success(true)
                                    .executionTimeMs(System.currentTimeMillis() - start)
                                    .build();
                        }
                    } catch (Exception ignored) {}

                    // Fallback 2: Check GitHub remote repository (https://github.com/sunny01saikumar/Nexus)
                    String githubContent = fetchFromGitHubRemote(pathStr);
                    if (githubContent != null && !githubContent.trim().isEmpty()) {
                        return ToolCall.builder()
                                .id(UUID.randomUUID().toString())
                                .toolName(getName())
                                .arguments(arguments)
                                .result("--- READ FROM GITHUB REPOSITORY (https://github.com/sunny01saikumar/Nexus/blob/main/" + pathStr + ") ---\n" + githubContent)
                                .success(true)
                                .executionTimeMs(System.currentTimeMillis() - start)
                                .build();
                    }

                    return ToolCall.builder()
                            .id(UUID.randomUUID().toString())
                            .toolName(getName())
                            .arguments(arguments)
                            .result("File not found: " + pathStr + " (Checked root directory: " + Paths.get(".").toAbsolutePath() + ")")
                            .success(false)
                            .executionTimeMs(System.currentTimeMillis() - start)
                            .build();
                }
            }

            if (Files.isDirectory(path)) {
                String tree = buildRecursiveDirectoryTree(path);
                // If local container directory tree contains only compiled app.jar / data, fallback to GitHub Repository Tree
                if (tree.contains("app.jar") && !tree.contains("backend") && !tree.contains("frontend")) {
                    String remoteTree = fetchGitHubRepositoryTree();
                    if (remoteTree != null && !remoteTree.trim().isEmpty()) {
                        tree = remoteTree;
                    }
                }

                return ToolCall.builder()
                        .id(UUID.randomUUID().toString())
                        .toolName(getName())
                        .arguments(arguments)
                        .result("Project Workspace Repository Tree [https://github.com/sunny01saikumar/Nexus]:\n" + tree)
                        .success(true)
                        .executionTimeMs(System.currentTimeMillis() - start)
                        .build();
            }

            String content = Files.readString(path);
            if (content.length() > 10000) {
                content = content.substring(0, 10000) + "\n...[truncated to 10,000 characters]";
            }

            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result(content)
                    .success(true)
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .build();

        } catch (Exception e) {
            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result("Error reading file: " + e.getMessage())
                    .success(false)
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    private String buildRecursiveDirectoryTree(Path root) {
        try (var stream = Files.walk(root, 4)) {
            List<String> relativePaths = stream
                    .filter(p -> !p.equals(root))
                    .map(root::relativize)
                    .map(Path::toString)
                    .filter(p -> !p.contains(".git") && !p.contains("node_modules") && !p.contains("target") && !p.contains(".idea") && !p.contains(".mvn"))
                    .limit(100)
                    .collect(java.util.stream.Collectors.toList());

            if (relativePaths.isEmpty()) return "Empty directory";
            return String.join("\n", relativePaths);
        } catch (Exception e) {
            return "Error walking directory: " + e.getMessage();
        }
    }

    private String getGitHubToken() {
        String token = System.getenv("GITHUB_TOKEN");
        if (token == null || token.trim().isEmpty()) token = System.getenv("GH_TOKEN");
        if (token == null || token.trim().isEmpty()) token = System.getenv("GITHUB_PAT");
        if (token == null || token.trim().isEmpty()) token = System.getenv("NEXUS_GITHUB_TOKEN");
        if (token == null || token.trim().isEmpty()) token = System.getProperty("github.token");
        return token != null ? token.trim() : null;
    }

    private String fetchFromGitHubRemote(String pathStr) {
        if (pathStr == null || pathStr.trim().isEmpty() || pathStr.equals("./") || pathStr.equals(".")) return null;
        try {
            String cleanPath = pathStr.startsWith("/") ? pathStr.substring(1) : pathStr;
            String token = getGitHubToken();

            // 1. Try GitHub REST API contents endpoint (supports private repositories with token)
            String apiUrl = "https://api.github.com/repos/sunny01saikumar/Nexus/contents/" + cleanPath;
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                    .build();

            var reqBuilder = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(apiUrl))
                    .timeout(java.time.Duration.ofSeconds(8))
                    .header("User-Agent", "NEXUS-AI-Engine")
                    .header("Accept", "application/vnd.github.v3.raw");

            if (token != null && !token.isEmpty()) {
                reqBuilder.header("Authorization", "token " + token);
            }

            java.net.http.HttpResponse<String> response = client.send(reqBuilder.GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null && !response.body().contains("404: Not Found")) {
                String body = response.body();
                if (body.length() > 10000) {
                    body = body.substring(0, 10000) + "\n...[truncated to 10,000 characters]";
                }
                return body;
            }

            // 2. Fallback to raw.githubusercontent.com
            String rawUrl = "https://raw.githubusercontent.com/sunny01saikumar/Nexus/main/" + cleanPath;
            var rawReqBuilder = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(rawUrl))
                    .timeout(java.time.Duration.ofSeconds(8))
                    .header("User-Agent", "NEXUS-AI-Engine");

            if (token != null && !token.isEmpty()) {
                rawReqBuilder.header("Authorization", "token " + token);
            }

            java.net.http.HttpResponse<String> rawResponse = client.send(rawReqBuilder.GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            if (rawResponse.statusCode() == 200 && rawResponse.body() != null && !rawResponse.body().contains("404: Not Found")) {
                String body = rawResponse.body();
                if (body.length() > 10000) {
                    body = body.substring(0, 10000) + "\n...[truncated to 10,000 characters]";
                }
                return body;
            }
        } catch (Exception e) {
            log.warn("GitHub remote fallback fetch error for [{}]: {}", pathStr, e.getMessage());
        }
        return null;
    }

    private String fetchGitHubRepositoryTree() {
        try {
            String apiUrl = "https://api.github.com/repos/sunny01saikumar/Nexus/git/trees/main?recursive=1";
            String token = getGitHubToken();

            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .build();

            var reqBuilder = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(apiUrl))
                    .timeout(java.time.Duration.ofSeconds(8))
                    .header("User-Agent", "NEXUS-AI-Engine")
                    .header("Accept", "application/vnd.github.v3+json");

            if (token != null && !token.isEmpty()) {
                reqBuilder.header("Authorization", "token " + token);
            }

            java.net.http.HttpResponse<String> response = client.send(reqBuilder.GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null) {
                String json = response.body();
                List<String> paths = new ArrayList<>();
                int idx = 0;
                while ((idx = json.indexOf("\"path\":\"", idx)) != -1) {
                    idx += 8;
                    int end = json.indexOf("\"", idx);
                    if (end != -1) {
                        String p = json.substring(idx, end);
                        if (!p.contains(".git") && !p.contains("target/") && !p.contains("node_modules/")) {
                            paths.add(p);
                        }
                        idx = end;
                    }
                }
                if (!paths.isEmpty()) {
                    return String.join("\n", paths.subList(0, Math.min(paths.size(), 80)));
                }
            }
        } catch (Exception e) {
            log.warn("GitHub repository tree fetch error: {}", e.getMessage());
        }
        return null;
    }
}
