package com.nexus.ai.tools;

import com.nexus.ai.domain.ToolCall;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Component
public class FileReaderTool implements Tool {

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
                return ToolCall.builder()
                        .id(UUID.randomUUID().toString())
                        .toolName(getName())
                        .arguments(arguments)
                        .result("Project Workspace Directory Tree [" + path.toAbsolutePath() + "]:\n" + tree)
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
}
