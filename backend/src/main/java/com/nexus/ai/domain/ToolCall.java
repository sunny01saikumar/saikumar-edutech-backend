package com.nexus.ai.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {
    private String id;
    private String toolName;
    private Map<String, Object> arguments;
    private String result;
    private boolean success;
    private Long executionTimeMs;
}
