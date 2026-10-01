package com.nexus.ai.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ToolSecurityUtils {

    private static final Logger log = LoggerFactory.getLogger(ToolSecurityUtils.class);

    private static final List<String> SENSITIVE_PATTERNS = Arrays.asList(
            ".env",
            "nexusdb",
            "data/",
            "/data",
            "\\data",
            "data\\",
            ".git",
            ".key",
            ".pem",
            ".crt",
            ".p12",
            ".jks",
            ".keystore",
            "application.yml",
            "application.properties",
            "application-prod",
            "app.jar",
            ".mv.db",
            ".lock.db",
            ".trace.db"
    );

    public static boolean isPathSensitive(String pathStr) {
        if (pathStr == null || pathStr.trim().isEmpty()) return false;
        String lower = pathStr.toLowerCase(Locale.ROOT).replace('\\', '/');

        // Allow workspace root directory requests ("./", ".", "", "/")
        if (lower.equals(".") || lower.equals("./") || lower.equals("/") || lower.trim().isEmpty()) {
            return false;
        }

        // Path traversal check
        if (lower.contains("../") || lower.contains("/..") || lower.contains("..\\") || lower.contains("\\..") || lower.equals("..")) {
            log.warn("Security Alert: Path traversal attempt detected in path [{}]", pathStr);
            return true;
        }

        for (String pattern : SENSITIVE_PATTERNS) {
            if (lower.contains(pattern)) {
                log.warn("Security Alert: Sensitive pattern [{}] matched in path [{}]", pattern, pathStr);
                return true;
            }
        }

        // Check file extension for database or key files
        if (lower.endsWith(".db") || lower.endsWith(".key") || lower.endsWith(".pem") || lower.endsWith(".env")) {
            log.warn("Security Alert: Sensitive file extension matched in path [{}]", pathStr);
            return true;
        }

        // Workspace boundary check
        try {
            Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
            Path targetPath = Paths.get(pathStr).toAbsolutePath().normalize();
            if (!targetPath.startsWith(projectRoot)) {
                log.warn("Security Alert: Target path [{}] is outside project root [{}]", targetPath, projectRoot);
                return true;
            }
        } catch (Exception e) {
            log.warn("Security Alert: Error checking target path boundary for [{}]: {}", pathStr, e.getMessage());
            return true;
        }

        return false;
    }

    public static boolean isCommandSensitive(String command) {
        if (command == null || command.trim().isEmpty()) return false;
        String lower = command.toLowerCase(Locale.ROOT).replace('\\', '/');

        for (String pattern : SENSITIVE_PATTERNS) {
            if (lower.contains(pattern)) {
                log.warn("Security Alert: Sensitive pattern [{}] matched in command [{}]", pattern, command);
                return true;
            }
        }

        if (lower.contains("/etc/passwd") || lower.contains("/etc/shadow") || lower.contains("c:/windows") || lower.contains("c:\\windows")) {
            log.warn("Security Alert: System file pattern matched in command [{}]", command);
            return true;
        }

        return false;
    }
}
