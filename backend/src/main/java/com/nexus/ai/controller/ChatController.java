package com.nexus.ai.controller;

import com.nexus.ai.domain.AiResponse;
import com.nexus.ai.domain.Conversation;
import com.nexus.ai.domain.Message;
import com.nexus.ai.orchestrator.AIOrchestrator;
import com.nexus.ai.service.ConversationService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class ChatController {

    private final AIOrchestrator aiOrchestrator;
    private final ConversationService conversationService;
    private final com.nexus.ai.security.JwtUtil jwtUtil;

    public ChatController(AIOrchestrator aiOrchestrator, ConversationService conversationService, com.nexus.ai.security.JwtUtil jwtUtil) {
        this.aiOrchestrator = aiOrchestrator;
        this.conversationService = conversationService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/chat")
    public AiResponse chat(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> request) {
        String message = request.get("message");
        String conversationId = request.get("conversationId");
        String provider = request.get("provider");
        String model = request.get("model");
        String systemPrompt = request.get("systemPrompt");
        String customApiKey = request.get("apiKey");
        if (customApiKey == null || customApiKey.trim().isEmpty()) {
            customApiKey = request.get("customApiKey");
        }

        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be empty.");
        }

        return aiOrchestrator.processChat(message, conversationId, provider, model, systemPrompt, customApiKey, authHeader);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> request) {
        String message = request.get("message");
        String conversationId = request.get("conversationId");
        String provider = request.get("provider");
        String model = request.get("model");
        String systemPrompt = request.get("systemPrompt");
        String customApiKey = request.get("apiKey");
        if (customApiKey == null || customApiKey.trim().isEmpty()) {
            customApiKey = request.get("customApiKey");
        }

        if (message == null || message.trim().isEmpty()) {
            return Flux.error(new IllegalArgumentException("Message content cannot be empty."));
        }

        return aiOrchestrator.processStreamingChat(message, conversationId, provider, model, systemPrompt, customApiKey, authHeader)
                .map(token -> {
                    try {
                        String encoded = java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8.name());
                        return ServerSentEvent.<String>builder()
                                .data(encoded)
                                .build();
                    } catch (Exception e) {
                        return ServerSentEvent.<String>builder()
                                .data(token)
                                .build();
                    }
                });
    }

    @GetMapping("/conversations")
    public List<Conversation> getConversations(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userEmail = jwtUtil != null ? jwtUtil.extractEmail(authHeader) : null;
        return conversationService.getConversationsForUser(userEmail);
    }

    @GetMapping("/conversations/{id}/messages")
    public List<Message> getMessages(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String id) {
        String userEmail = jwtUtil != null ? jwtUtil.extractEmail(authHeader) : null;
        return conversationService.getMessagesForConversation(id, userEmail);
    }

    @DeleteMapping("/conversations/{id}")
    public Map<String, String> deleteConversation(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String id) {
        String userEmail = jwtUtil != null ? jwtUtil.extractEmail(authHeader) : null;
        conversationService.deleteConversation(id, userEmail);
        return Map.of("status", "deleted", "id", id);
    }
}
