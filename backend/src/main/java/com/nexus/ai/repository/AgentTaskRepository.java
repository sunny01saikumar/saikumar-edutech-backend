package com.nexus.ai.repository;

import com.nexus.ai.domain.AgentTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentTaskRepository extends JpaRepository<AgentTask, String> {
    List<AgentTask> findAllByOrderByCreatedAtDesc();
}
