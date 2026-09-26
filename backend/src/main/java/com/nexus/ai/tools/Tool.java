package com.nexus.ai.tools;

import com.nexus.ai.domain.ToolCall;

import java.util.Map;

public interface Tool {
    String getName();
    String getDescription();
    Map<String, Object> getInputSchema();
    ToolCall execute(Map<String, Object> arguments);
}
