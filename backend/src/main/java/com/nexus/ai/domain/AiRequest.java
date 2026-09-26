package com.nexus.ai.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRequest {
    private String conversationId;
    private String systemPrompt;
    private List<ChatMessage> messages;
    private String requestedProvider;
    private String requestedModel;
    private String customApiKey;
    private Double temperature;
    private Integer maxTokens;
    private Boolean stream;
    private List<Map<String, Object>> tools;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage {
        private String role; // "system", "user", "assistant", "tool"
        private String content;
        private String name;
    }
}
