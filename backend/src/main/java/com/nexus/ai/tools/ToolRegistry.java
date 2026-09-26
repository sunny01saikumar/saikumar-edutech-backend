package com.nexus.ai.tools;

import com.nexus.ai.domain.ToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);
    private final Map<String, Tool> tools = new HashMap<>();

    public ToolRegistry(List<Tool> toolList) {
        for (Tool tool : toolList) {
            tools.put(tool.getName().toLowerCase(), tool);
            log.info("Registered Tool: [{}]", tool.getName());
        }
    }

    public Tool getTool(String name) {
        return tools.get(name.toLowerCase());
    }

    public List<Map<String, Object>> getToolSchemas() {
        List<Map<String, Object>> schemas = new ArrayList<>();
        for (Tool tool : tools.values()) {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("name", tool.getName());
            schema.put("description", tool.getDescription());
            schema.put("parameters", tool.getInputSchema());
            schemas.add(schema);
        }
        return schemas;
    }

    public ToolCall executeTool(String toolName, Map<String, Object> args) {
        Tool tool = getTool(toolName);
        if (tool == null) {
            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(toolName)
                    .arguments(args)
                    .result("Tool not found: " + toolName)
                    .success(false)
                    .executionTimeMs(0L)
                    .build();
        }
        return tool.execute(args);
    }
}
