package com.nexus.ai.repository;

import com.nexus.ai.domain.AgentStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentStepRepository extends JpaRepository<AgentStep, String> {
    List<AgentStep> findByTaskIdOrderByStepNumberAsc(String taskId);
}
