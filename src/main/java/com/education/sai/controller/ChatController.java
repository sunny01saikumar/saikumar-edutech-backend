package com.education.sai.controller;

import com.education.sai.dto.SendMessageRequest;
import com.education.sai.model.MessageClass;
import com.education.sai.service.ChatService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/send")
    public MessageClass send(@RequestBody SendMessageRequest request) {
        return chatService.sendMessage(request);
    }

    @GetMapping("/conversation")
    public List<MessageClass> conversation(@RequestParam Long user1, @RequestParam Long user2) {
        return chatService.getConversation(user1, user2);
    }
}