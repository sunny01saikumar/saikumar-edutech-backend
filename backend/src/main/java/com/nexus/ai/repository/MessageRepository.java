package com.nexus.ai.repository;

import com.nexus.ai.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {
    List<Message> findByConversationIdOrderByTimestampAsc(String conversationId);
    void deleteByConversationId(String conversationId);
}
