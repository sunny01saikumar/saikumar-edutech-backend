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

    public ChatController(AIOrchestrator aiOrchestrator, ConversationService conversationService) {
        this.aiOrchestrator = aiOrchestrator;
        this.conversationService = conversationService;
    }

    @PostMapping("/chat")
    public AiResponse chat(@RequestBody Map<String, String> request) {
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

        return aiOrchestrator.processChat(message, conversationId, provider, model, systemPrompt, customApiKey);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody Map<String, String> request) {
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

        return aiOrchestrator.processStreamingChat(message, conversationId, provider, model, systemPrompt, customApiKey)
                .map(token -> ServerSentEvent.<String>builder()
                        .data(token)
                        .build());
    }

    @GetMapping("/conversations")
    public List<Conversation> getConversations() {
        return conversationService.getAllConversations();
    }

    @GetMapping("/conversations/{id}/messages")
    public List<Message> getMessages(@PathVariable String id) {
        return conversationService.getMessagesForConversation(id);
    }

    @DeleteMapping("/conversations/{id}")
    public Map<String, String> deleteConversation(@PathVariable String id) {
        conversationService.deleteConversation(id);
        return Map.of("status", "deleted", "id", id);
    }
}
