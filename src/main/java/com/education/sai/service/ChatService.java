package com.education.sai.service;

import com.education.sai.dto.SendMessageRequest;
import com.education.sai.model.MessageClass;
import com.education.sai.model.User;
import com.education.sai.repo.MessageRepository;
import com.education.sai.repo.UserRepository;

import com.education.sai.security.AuthUtil;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;


    public MessageClass sendMessage(SendMessageRequest request) {
        String email = AuthUtil.getCurrentUserEmail();
        User sender = userRepository.findByEmail(email).orElseThrow();
        MessageClass message = MessageClass.builder().senderId(sender.getId())
                .receiverId(request.getReceiverId()).content(request.getContent())
                .createdAt(LocalDateTime.now())
                .build();
        return messageRepository.save(message);
    }

    public List<MessageClass> getConversation(Long user1, Long user2) {
        return messageRepository.getConversation(user1, user2);
    }
}