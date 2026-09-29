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
        return getOrCreateConversation(conversationId, firstUserMessage, null);
    }

    @Transactional
    public Conversation getOrCreateConversation(String conversationId, String firstUserMessage, String userEmail) {
        if (conversationId != null && !conversationId.trim().isEmpty()) {
            String cleanId = conversationId.trim();
            Conversation conv = conversationRepository.findById(cleanId)
                    .orElseGet(() -> createNewConversation(cleanId, firstUserMessage, userEmail));

            if (conv.getUserEmail() == null && userEmail != null && !userEmail.trim().isEmpty()) {
                conv.setUserEmail(userEmail.trim());
                conv = conversationRepository.save(conv);
            }
            return conv;
        }
        return createNewConversation(null, firstUserMessage, userEmail);
    }

    private Conversation createNewConversation(String customId, String titlePrompt, String userEmail) {
        String title = (titlePrompt != null && !titlePrompt.trim().isEmpty())
                ? (titlePrompt.length() > 30 ? titlePrompt.substring(0, 30) + "..." : titlePrompt)
                : "New Chat";
        Conversation conv = Conversation.builder()
                .id(customId != null && !customId.trim().isEmpty() ? customId.trim() : null)
                .title(title)
                .userEmail(userEmail != null && !userEmail.trim().isEmpty() ? userEmail.trim() : null)
                .build();
        return conversationRepository.save(conv);
    }

    public List<Conversation> getAllConversations() {
        return conversationRepository.findAllByOrderByUpdatedAtDesc();
    }

    public List<Conversation> getConversationsForUser(String userEmail) {
        if (userEmail != null && !userEmail.trim().isEmpty()) {
            return conversationRepository.findByUserEmailOrderByUpdatedAtDesc(userEmail.trim());
        }
        return conversationRepository.findByUserEmailIsNullOrderByUpdatedAtDesc();
    }

    public List<Message> getMessagesForConversation(String conversationId) {
        return getMessagesForConversation(conversationId, null);
    }

    public List<Message> getMessagesForConversation(String conversationId, String userEmail) {
        if (conversationId == null || conversationId.trim().isEmpty()) return List.of();
        String cleanId = conversationId.trim();

        if (userEmail != null && !userEmail.trim().isEmpty()) {
            Conversation conv = conversationRepository.findById(cleanId).orElse(null);
            if (conv != null && conv.getUserEmail() != null && !conv.getUserEmail().equalsIgnoreCase(userEmail.trim())) {
                return List.of();
            }
        }
        return messageRepository.findByConversationIdOrderByTimestampAsc(cleanId);
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
        deleteConversation(id, null);
    }

    @Transactional
    public void deleteConversation(String id, String userEmail) {
        if (id == null || id.trim().isEmpty()) return;
        String cleanId = id.trim();
        Conversation conv = conversationRepository.findById(cleanId).orElse(null);
        if (conv != null) {
            if (conv.getUserEmail() != null && userEmail != null && !userEmail.trim().isEmpty()) {
                if (!conv.getUserEmail().equalsIgnoreCase(userEmail.trim())) {
                    return; // Unauthorized to delete another user's conversation
                }
            }
            messageRepository.deleteByConversationId(cleanId);
            conversationRepository.deleteById(cleanId);
        }
    }
}
