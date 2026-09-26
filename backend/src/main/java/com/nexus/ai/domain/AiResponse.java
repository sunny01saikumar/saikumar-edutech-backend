package com.nexus.ai.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiResponse {
    private String id;
    private String conversationId;
    private String content;
    private String provider;
    private String model;
    private AiUsage usage;
    private Long latencyMs;
    private List<ToolCall> toolCalls;
    private String finishReason;
    private String traceId;
}
