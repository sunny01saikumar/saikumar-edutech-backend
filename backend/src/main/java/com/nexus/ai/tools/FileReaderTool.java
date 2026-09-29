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
                try (var stream = Files.list(path)) {
                    String list = stream.map(p -> p.getFileName().toString())
                            .limit(50)
                            .reduce((a, b) -> a + "\n" + b)
                            .orElse("Empty directory");
                    return ToolCall.builder()
                            .id(UUID.randomUUID().toString())
                            .toolName(getName())
                            .arguments(arguments)
                            .result("Directory contents for [" + path.toAbsolutePath() + "]:\n" + list)
                            .success(true)
                            .executionTimeMs(System.currentTimeMillis() - start)
                            .build();
                }
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
}
