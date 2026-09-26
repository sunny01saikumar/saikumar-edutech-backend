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
        String pathStr = String.valueOf(arguments.get("filePath"));

        try {
            Path path = Paths.get(pathStr);
            if (!Files.exists(path)) {
                return ToolCall.builder()
                        .id(UUID.randomUUID().toString())
                        .toolName(getName())
                        .arguments(arguments)
                        .result("File not found: " + pathStr)
                        .success(false)
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
}
