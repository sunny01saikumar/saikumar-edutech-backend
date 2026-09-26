package com.nexus.ai.tools;

import com.nexus.ai.domain.ToolCall;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

@Component
public class GitTool implements Tool {

    @Override
    public String getName() {
        return "git";
    }

    @Override
    public String getDescription() {
        return "Runs git commands like status, log, diff, branch, or show.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "subcommand", Map.of("type", "string", "description", "Git subcommand (e.g. 'status', 'log -n 5', 'diff')")
                ),
                "required", List.of("subcommand")
        );
    }

    @Override
    public ToolCall execute(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        String subcommand = String.valueOf(arguments.get("subcommand"));

        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            ProcessBuilder pb = new ProcessBuilder();
            if (isWindows) {
                pb.command("cmd.exe", "/c", "git " + subcommand);
            } else {
                pb.command("sh", "-c", "git " + subcommand);
            }

            pb.redirectErrorStream(true);
            Process p = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            p.waitFor();
            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result(output.toString())
                    .success(p.exitValue() == 0)
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .build();

        } catch (Exception e) {
            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result("Git tool error: " + e.getMessage())
                    .success(false)
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .build();
        }
    }
}
