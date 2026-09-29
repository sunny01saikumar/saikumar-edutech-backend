package com.nexus.ai.repository;

import com.nexus.ai.domain.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String> {
    List<Conversation> findAllByOrderByUpdatedAtDesc();
    List<Conversation> findByUserEmailOrderByUpdatedAtDesc(String userEmail);
    List<Conversation> findByUserEmailIsNullOrderByUpdatedAtDesc();
    Optional<Conversation> findByIdAndUserEmail(String id, String userEmail);
}
