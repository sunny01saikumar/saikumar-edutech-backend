package com.nexus.ai.controller;

import com.nexus.ai.agent.CodingAgent;
import com.nexus.ai.domain.AgentStep;
import com.nexus.ai.domain.AgentTask;
import com.nexus.ai.repository.AgentStepRepository;
import com.nexus.ai.repository.AgentTaskRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/agent")
public class AgentController {

    private final CodingAgent codingAgent;
    private final AgentTaskRepository agentTaskRepository;
    private final AgentStepRepository agentStepRepository;

    public AgentController(CodingAgent codingAgent,
                           AgentTaskRepository agentTaskRepository,
                           AgentStepRepository agentStepRepository) {
        this.codingAgent = codingAgent;
        this.agentTaskRepository = agentTaskRepository;
        this.agentStepRepository = agentStepRepository;
    }

    @PostMapping("/run")
    public AgentTask runTask(@RequestBody Map<String, String> body) {
        String goal = body.get("goal");
        String verificationCommand = body.get("verificationCommand");
        String provider = body.get("provider");
        String model = body.get("model");

        if (goal == null || goal.trim().isEmpty()) {
            throw new IllegalArgumentException("Agent goal cannot be empty");
        }

        return codingAgent.executeCodingTask(goal, verificationCommand, provider, model);
    }

    @GetMapping("/tasks")
    public List<AgentTask> getTasks() {
        return agentTaskRepository.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/tasks/{id}/steps")
    public List<AgentStep> getTaskSteps(@PathVariable String id) {
        return agentStepRepository.findByTaskIdOrderByStepNumberAsc(id);
    }
}
