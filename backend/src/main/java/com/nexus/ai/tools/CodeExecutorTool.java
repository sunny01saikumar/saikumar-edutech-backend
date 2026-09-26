package com.nexus.ai.tools;

import com.nexus.ai.domain.ToolCall;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class CodeExecutorTool implements Tool {

    @Override
    public String getName() {
        return "code_executor";
    }

    @Override
    public String getDescription() {
        return "Executes shell commands, scripts, builds, or unit tests (e.g. 'npm test', 'mvn test', 'python script.py').";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "command", Map.of("type", "string", "description", "Command line string to execute"),
                        "workingDirectory", Map.of("type", "string", "description", "Optional working directory path")
                ),
                "required", List.of("command")
        );
    }

    @Override
    public ToolCall execute(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        String command = String.valueOf(arguments.get("command"));
        String workingDirStr = arguments.containsKey("workingDirectory") ? String.valueOf(arguments.get("workingDirectory")) : ".";

        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            ProcessBuilder processBuilder = new ProcessBuilder();
            if (isWindows) {
                processBuilder.command("cmd.exe", "/c", command);
            } else {
                processBuilder.command("sh", "-c", command);
            }

            processBuilder.directory(Paths.get(workingDirStr).toFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (output.length() > 20000) {
                        output.append("\n...[output truncated at 20,000 characters]");
                        break;
                    }
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ToolCall.builder()
                        .id(UUID.randomUUID().toString())
                        .toolName(getName())
                        .arguments(arguments)
                        .result("Execution timed out after 30 seconds.\nOutput so far:\n" + output)
                        .success(false)
                        .executionTimeMs(System.currentTimeMillis() - start)
                        .build();
            }

            int exitCode = process.exitValue();
            boolean success = (exitCode == 0);

            String resultText = "Exit Code: " + exitCode + "\nOutput:\n" + output;

            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result(resultText)
                    .success(success)
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .build();

        } catch (Exception e) {
            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result("Execution error: " + e.getMessage())
                    .success(false)
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .build();
        }
    }
}
