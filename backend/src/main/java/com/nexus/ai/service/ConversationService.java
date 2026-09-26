package com.nexus.ai.service;

import com.nexus.ai.domain.Conversation;
import com.nexus.ai.domain.Message;
import com.nexus.ai.domain.MessageRole;
import com.nexus.ai.repository.ConversationRepository;
import com.nexus.ai.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public ConversationService(ConversationRepository conversationRepository, MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public Conversation getOrCreateConversation(String conversationId, String firstUserMessage) {
        if (conversationId != null && !conversationId.isEmpty()) {
            return conversationRepository.findById(conversationId).orElseGet(() -> createNewConversation(firstUserMessage));
        }
        return createNewConversation(firstUserMessage);
    }

    private Conversation createNewConversation(String titlePrompt) {
        String title = (titlePrompt != null && !titlePrompt.trim().isEmpty())
                ? (titlePrompt.length() > 30 ? titlePrompt.substring(0, 30) + "..." : titlePrompt)
                : "New Chat";
        Conversation conv = Conversation.builder()
                .title(title)
                .build();
        return conversationRepository.save(conv);
    }

    public List<Conversation> getAllConversations() {
        return conversationRepository.findAllByOrderByUpdatedAtDesc();
    }

    public List<Message> getMessagesForConversation(String conversationId) {
        return messageRepository.findByConversationIdOrderByTimestampAsc(conversationId);
    }

    @Transactional
    public Message saveMessage(String conversationId, MessageRole role, String content, String provider, String model,
                               Integer promptTokens, Integer completionTokens, Long latencyMs) {
        Message msg = Message.builder()
                .conversationId(conversationId)
                .role(role)
                .content(content)
                .providerUsed(provider)
                .modelUsed(model)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .latencyMs(latencyMs)
                .build();

        Message saved = messageRepository.save(msg);

        // Update conversation timestamp
        conversationRepository.findById(conversationId).ifPresent(c -> {
            c.onUpdate();
            conversationRepository.save(c);
        });

        return saved;
    }

    @Transactional
    public void deleteConversation(String id) {
        messageRepository.deleteByConversationId(id);
        conversationRepository.deleteById(id);
    }
}
