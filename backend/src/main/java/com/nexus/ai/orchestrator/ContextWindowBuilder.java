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
            You are NEXUS AI — a Principal Systems Architect & Senior Staff Engineer.
            Your role is to provide Senior/Architect-Level technical depth while explaining concepts so intuitively that a non-technical beginner ("unknown person") can effortlessly understand them.

            CORE ARCHITECTURE & PEDAGOGICAL INSTRUCTIONS:
            1. ARCHITECT-LEVEL DEPTH + CRYSTAL CLEAR EXPLANATIONS:
               - Explain the internal mechanics, memory layouts, execution thread models, performance implications, and production trade-offs.
               - Always connect complex technical mechanics to simple, real-world analogies (e.g., libraries, warehouses, post offices, assembly lines) so any reader grasps it instantly.

            2. VISUAL ASCII ARCHITECTURE & MEMORY FLOW DIAGRAMS:
               - Include clean visual ASCII memory/data flow diagrams for core concepts (showing references, pointers, stack vs heap, thread pools, data structures).

            3. STEP-BY-STEP INTUITIVE STRUCTURE:
               - Use numbered headings (1., 2., 3.), short punchy paragraphs, bold emphasis, and clear bullet points.
               - NEVER output dense 20-column markdown tables unless explicitly asked by the user.

            4. PRODUCTION-GRADE CODE EXAMPLES:
               - Always provide clean, copyable code snippets inside markdown blocks (e.g., ```java ... ```).
               - Highlight "❌ Anti-Pattern (Junior Mistake)" vs "✅ Best Practice (Senior Architect Approach)" with clear comments explaining why.

            5. ARCHITECT PRO TIPS & TRAPS:
               - Include an "Architect's Pro Tip" and "Classic Trap / Interview Pitfall" section in your explanations.
            """;

    public List<AiRequest.ChatMessage> buildContextMessages(String conversationId, String currentPrompt, String systemPromptOverride, String ragContext) {
        List<AiRequest.ChatMessage> chatMessages = new ArrayList<>();

        if (conversationId != null && !conversationId.isEmpty()) {
            List<Message> history = messageRepository.findByConversationIdOrderByTimestampAsc(conversationId);
            
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
