package com.nexus.ai.orchestrator;

import com.nexus.ai.domain.AiRequest;
import com.nexus.ai.domain.Message;
import com.nexus.ai.domain.MessageRole;
import com.nexus.ai.repository.MessageRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ContextWindowBuilder {

    private final MessageRepository messageRepository;

    public ContextWindowBuilder(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    public static final String DEFAULT_SYSTEM_PROMPT = """
            You are NEXUS AI, an intelligent, high-precision AI assistant.

            MANDATORY RESPONSE FORMATTING INSTRUCTIONS:
            1. STRUCTURE & SEQUENTIAL STEPPING:
               - Organize your response using clear Markdown section headings (## and ###).
               - Break complex topics into logical, sequential steps (e.g. Step 1, Step 2 or 1., 2., 3.).
               - Use clean bullet points (* or -) on separate lines for lists.
               - ALWAYS insert double line breaks between paragraphs, headers, and code blocks to ensure clean visual spacing.

            2. CODE & TECHNICAL EXAMPLES:
               - Wrap all code snippets in fenced code blocks with the exact language specified (e.g. ```java, ```javascript, ```python).
               - Add clear inline comments explaining important logic.

            3. VISUAL CLARITY & ACCURACY:
               - Bold important key terms (**term**) and annotations.
               - Do NOT output raw unformatted internal scratchpads or unescaped math prompt templates.
               - Use standard Unicode arrows (-> or =>) for flow descriptions.
               - Be accurate, precise, and direct. Do not fabricate facts.
            """;

    public List<AiRequest.ChatMessage> buildContextMessages(String conversationId, String currentPrompt, String systemPromptOverride, String ragContext) {
        List<AiRequest.ChatMessage> chatMessages = new ArrayList<>();

        // Fetch history if conversationId is provided
        if (conversationId != null && !conversationId.isEmpty()) {
            List<Message> history = messageRepository.findByConversationIdOrderByTimestampAsc(conversationId);
            
            // Apply context window constraint (take last 20 messages to prevent context overflow)
            int startIndex = Math.max(0, history.size() - 20);
            for (int i = startIndex; i < history.size(); i++) {
                Message msg = history.get(i);
                chatMessages.add(AiRequest.ChatMessage.builder()
                        .role(msg.getRole().name().toLowerCase())
                        .content(msg.getContent())
                        .name(msg.getName())
                        .build());
            }
        }

        // Add current user prompt if not already last in history
        if (currentPrompt != null && !currentPrompt.isEmpty()) {
            boolean alreadyInHistory = !chatMessages.isEmpty() && 
                    chatMessages.get(chatMessages.size() - 1).getContent().equals(currentPrompt);
            if (!alreadyInHistory) {
                chatMessages.add(AiRequest.ChatMessage.builder()
                        .role("user")
                        .content(currentPrompt)
                        .build());
            }
        }

        return chatMessages;
    }

    public String buildSystemPrompt(String customSystemPrompt, String ragContext) {
        StringBuilder sb = new StringBuilder();
        if (customSystemPrompt != null && !customSystemPrompt.trim().isEmpty()) {
            sb.append(customSystemPrompt);
        } else {
            sb.append(DEFAULT_SYSTEM_PROMPT);
        }

        if (ragContext != null && !ragContext.trim().isEmpty()) {
            sb.append("\n\n--- RETRIEVED DOCUMENT KNOWLEDGE (RAG) ---\n");
            sb.append(ragContext);
            sb.append("\n-----------------------------------------\n");
            sb.append("Use the retrieved document knowledge above when applicable to answer user questions accurately.");
        }

        return sb.toString();
    }
}
