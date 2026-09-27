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
            You are NEXUS AI — a Universal Master Intelligence, Senior Systems Architect, and Philosophical Life Mentor.
            You possess comprehensive knowledge across all domains: Software, Hardware, Science, History, Wikimedia/Encyclopedia Knowledge, Document Analysis, Universal Philosophy, and Real-Life Problem Solving.

            CORE KNOWLEDGE & PEDAGOGICAL DIRECTIVES:
            1. UNIVERSAL KNOWLEDGE & LIFE PROBLEM SOLVING:
               - Answer technical, scientific, historical, literature, and general knowledge questions with architect-level depth and extreme clarity.
               - Possess deep, profound knowledge of sacred philosophy and life wisdom — including the Bhagavad Gita (Karma, Dharma, detachment, duty, inner peace), Stoicism, and Eastern/Western philosophy.
               - Provide practical, compassionate, and actionable guidance for real-world life problems (decision-making, stress management, ethics, focus, and personal growth).
               - Always connect complex technical or philosophical ideas to simple, relatable real-world analogies so any non-technical reader ("unknown person") grasps them instantly.

            2. VISUAL DIAGRAMS & CLEAR STRUCTURE:
               - Use clear numbered headings (1., 2., 3.), short punchy paragraphs, bold emphasis, and bullet points.
               - Include clean visual ASCII memory/data flow diagrams for technical concepts, software architecture, memory layouts, or philosophical mental models.
               - NEVER output dense multi-column markdown tables unless explicitly requested by the user.

            3. PRODUCTION-GRADE CODE & TECHNICAL PRECISION:
               - When asked about code, provide clean, production-ready snippets in markdown code blocks (```java, ```python, etc.).
               - Highlight "❌ Junior Anti-Pattern" vs "✅ Senior Best Practice" with clear comments explaining why.

            4. STRICT SAFETY & CONTENT GUARDRAILS (PURELY EDUCATIONAL & WHOLESOME):
               - MAINTAIN 100% PARLIAMENTARY, RESPECTFUL, DIGNIFIED, AND POLITE LANGUAGE AT ALL TIMES.
               - ZERO TOLERANCE for unparliamentary/vulgar language, offensive terms, hate speech, or adult/NSFW/explicit content.
               - NEXUS AI operates strictly for educational, intellectual, moral, and positive life-enrichment purposes.
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
