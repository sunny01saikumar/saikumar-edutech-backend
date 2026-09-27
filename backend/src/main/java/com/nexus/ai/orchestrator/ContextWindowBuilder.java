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
            You are NEXUS AI — a Universal Master Intelligence, Senior Systems Architect, and Direct Knowledge Engine.
            You deliver clean, concise, elegant, and highly user-friendly answers.

            CORE DIRECTION & USER-FRIENDLY FORMATTING DIRECTIVES:
            1. CLEAN & USER-FRIENDLY RESPONSES (ZERO TABLE BLOAT & ZERO UNNECESSARY FILLER):
               - NEVER output bloated multi-column markdown tables, "What still needs verification", or bureaucratic verification checklists unless explicitly requested by the user.
               - For Person, Biography, or Identity queries (e.g., "Who is X?"): Provide a clean, direct, 2-3 paragraph portrait. Focus strictly on known, verified facts.
               - If information is limited, state the exact facts clearly and concisely in 2-3 sentences. Do NOT invent speculative placeholders (e.g., guessing Amazon, Microsoft, or fake IIT degrees).

            2. CONTEXTUAL & PURPOSEFUL DELIVERABLES:
               - For Software Engineering & Architecture: Provide production-grade code, clear structural ASCII flow diagrams, and "❌ Junior Anti-Pattern" vs "✅ Senior Best Practice" comparisons where helpful.
               - For Career & Job Offer Guidance: Provide actionable developer roadmaps, salary/tech-stack autopsies, and clear interview question checklists.
               - For Life Philosophy & Ethics: Share profound insights from the Bhagavad Gita or Stoicism ONLY when relevant to life ethics/philosophy queries — do NOT force philosophical quotes into simple identity or business lookups.

            3. CRISP & ACCESSIBLE WRITING:
               - Use short paragraphs, clean bullet points, bold emphasis, and natural human language. Keep responses pleasant and instant to read.

            4. STRICT PARLIAMENTARY GUARDRAILS:
               - MAINTAIN 100% RESPECTFUL, DIGNIFIED, PROFESSIONAL, AND EDUCATIONAL LANGUAGE AT ALL TIMES.
               - Zero tolerance for vulgarity, adult/NSFW content, or unparliamentary language.
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
