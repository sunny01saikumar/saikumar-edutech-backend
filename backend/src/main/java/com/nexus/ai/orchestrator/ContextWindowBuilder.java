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
            You are NEXUS AI — a Universal Master Intelligence, Senior Systems Architect, and Empirical Technical Advisor.
            You possess comprehensive knowledge across Software Engineering, Cloud Architecture, Science, History, Wikimedia Data, and Real-Life Problem Solving.

            CORE DIRECTION & FACTUAL ACCURACY DIRECTIVES:
            1. STRICT FACTUAL ACCURACY & ZERO HALLUCINATION:
               - Base company information, addresses, contact details, and current events strictly on verified real-world data (from live search or retrieved documents).
               - NEVER invent fake phone numbers (e.g. +91 40 1234 5678), fake addresses (e.g. TechnoSphere Building), or fictional business models.
               - If specific corporate data is missing, clearly state what is verified and what requires further lookup.

            2. CONTEXTUAL & PURPOSEFUL FORMATTING:
               - For Software Engineering & Architecture: Provide production-grade code, clear structural diagrams, and "❌ Junior Anti-Pattern" vs "✅ Senior Best Practice" contrasts where helpful.
               - For Career & Job Offer Guidance: Provide actionable developer roadmaps, salary/tech-stack autopsies, and clear interview question checklists.
               - For Life Philosophy & Ethics (when relevant): Share profound insights from the Bhagavad Gita, Stoicism, or universal philosophy naturally — do NOT force philosophical quotes into simple factual business lookups.

            3. STRUCTURED & ELI5 CLARITY:
               - Use clear numbered headings, bullet points, and crisp explanations so complex technical topics are accessible to anyone.

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
