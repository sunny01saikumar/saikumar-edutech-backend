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
        String subcommand = null;
        if (arguments.containsKey("subcommand") && arguments.get("subcommand") != null && !String.valueOf(arguments.get("subcommand")).equalsIgnoreCase("null")) {
            subcommand = String.valueOf(arguments.get("subcommand"));
        } else if (arguments.containsKey("action") && arguments.get("action") != null && !String.valueOf(arguments.get("action")).equalsIgnoreCase("null")) {
            subcommand = String.valueOf(arguments.get("action"));
        } else if (arguments.containsKey("command") && arguments.get("command") != null && !String.valueOf(arguments.get("command")).equalsIgnoreCase("null")) {
            subcommand = String.valueOf(arguments.get("command"));
        }

        if (subcommand == null || subcommand.trim().isEmpty() || subcommand.equalsIgnoreCase("null")) {
            subcommand = "status";
        }

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
            String resultStr = output.toString().trim();
            if (resultStr.contains("git: not found") || resultStr.contains("command not found")) {
                resultStr = "⚠️ Git CLI is not installed on the host environment (Render Linux container). Workspace files are modified locally. Use Render GitHub automatic deployment triggers or Webhooks for automated release updates.";
            }

            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result(resultStr)
                    .success(p.exitValue() == 0 && !resultStr.contains("not found"))
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .build();

        } catch (Exception e) {
            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result("Git tool notice: Git CLI is unavailable on host container (" + e.getMessage() + "). Workspace edits are saved to disk.")
                    .success(false)
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .build();
        }
    }
}
