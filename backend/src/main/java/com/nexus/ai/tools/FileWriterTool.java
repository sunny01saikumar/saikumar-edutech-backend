package com.nexus.ai.tools;

import com.nexus.ai.domain.ToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

@Component
public class FileWriterTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(FileWriterTool.class);

    @Override
    public String getName() {
        return "file_writer";
    }

    @Override
    public String getDescription() {
        return "Creates or updates a file on disk within the project workspace with specified code content.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "filePath", Map.of("type", "string", "description", "Relative or absolute target file path to write"),
                        "content", Map.of("type", "string", "description", "The complete code or file content to write")
                ),
                "required", List.of("filePath", "content")
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

        String content = null;
        if (arguments.containsKey("content") && arguments.get("content") != null) {
            content = String.valueOf(arguments.get("content"));
        } else if (arguments.containsKey("code") && arguments.get("code") != null) {
            content = String.valueOf(arguments.get("code"));
        } else if (arguments.containsKey("text") && arguments.get("text") != null) {
            content = String.valueOf(arguments.get("text"));
        }

        if (pathStr == null || pathStr.trim().isEmpty() || pathStr.equalsIgnoreCase("null")) {
            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result("Error: Target file path is missing or null.")
                    .success(false)
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .build();
        }

        if (ToolSecurityUtils.isPathSensitive(pathStr)) {
            log.warn("Access denied writing to sensitive target path: [{}]", pathStr);
            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result("Security Exception: Writing to sensitive system/database file or directory [" + pathStr + "] is restricted.")
                    .success(false)
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .build();
        }

        if (content == null) {
            content = "";
        }

        try {
            Path path = Paths.get(pathStr);

            // Handle Windows absolute path translation for Linux hosting containers
            if (!Files.exists(path) && (pathStr.contains(":\\") || pathStr.contains(":/"))) {
                String fileName = Paths.get(pathStr).getFileName() != null ? Paths.get(pathStr).getFileName().toString() : "output.txt";
                path = Paths.get("./" + fileName);
            }

            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("FileWriterTool wrote {} bytes to file [{}]", content.length(), path.toAbsolutePath());

            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result("File [" + path.toAbsolutePath() + "] updated successfully (" + content.length() + " characters written).")
                    .success(true)
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .build();

        } catch (Exception e) {
            log.error("FileWriterTool failed to write to path [{}]: {}", pathStr, e.getMessage());
            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result("Error writing to file [" + pathStr + "]: " + e.getMessage())
                    .success(false)
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .build();
        }
    }
}
