package com.nexus.ai.agent;

import com.nexus.ai.domain.*;
import com.nexus.ai.orchestrator.ModelRouter;
import com.nexus.ai.provider.AIProvider;
import com.nexus.ai.repository.AgentStepRepository;
import com.nexus.ai.repository.AgentTaskRepository;
import com.nexus.ai.tools.ToolRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AgentLoopEngine {

    private static final Logger log = LoggerFactory.getLogger(AgentLoopEngine.class);

    private static final int MAX_STEPS = 10;
    private final ModelRouter modelRouter;
    private final ToolRegistry toolRegistry;
    private final AgentTaskRepository agentTaskRepository;
    private final AgentStepRepository agentStepRepository;
    private final VerifierService verifierService;

    public AgentLoopEngine(ModelRouter modelRouter,
                           ToolRegistry toolRegistry,
                           AgentTaskRepository agentTaskRepository,
                           AgentStepRepository agentStepRepository,
                           VerifierService verifierService) {
        this.modelRouter = modelRouter;
        this.toolRegistry = toolRegistry;
        this.agentTaskRepository = agentTaskRepository;
        this.agentStepRepository = agentStepRepository;
        this.verifierService = verifierService;
    }

    public AgentTask executeTaskLoop(String goal, String providerName, String modelName) {
        AgentTask task = AgentTask.builder()
                .goal(goal)
                .status("RUNNING")
                .totalSteps(0)
                .build();
        task = agentTaskRepository.save(task);

        log.info("Starting Agent Execution Loop for Task [{}]: {}", task.getId(), goal);

        List<AiRequest.ChatMessage> messages = new ArrayList<>();
        messages.add(AiRequest.ChatMessage.builder()
                .role("system")
                .content("""
                        You are an Autonomous AI Agent.
                        Your task is: %s
                        
                        You can use available tools by replying in format:
                        TOOL: <tool_name>
                        ARGS: {"arg1": "val1"}
                        
                        When the task is complete, reply with:
                        FINAL: <summary of final result>
                        """.formatted(goal))
                .build());

        messages.add(AiRequest.ChatMessage.builder()
                .role("user")
                .content("Begin working on task: " + goal)
                .build());

        int stepCount = 0;
        boolean finished = false;
        String finalOutput = "";

        ModelRouter.RouteDecision route = modelRouter.route(goal, providerName, modelName);
        AIProvider provider = route.getProvider();

        while (stepCount < MAX_STEPS && !finished) {
            stepCount++;
            long stepStart = System.currentTimeMillis();

            AiRequest request = AiRequest.builder()
                    .messages(messages)
                    .requestedModel(route.getTargetModel())
                    .build();

            AiResponse response = provider.generate(request);
            String aiContent = response.getContent() != null ? response.getContent().trim() : "";

            if (aiContent.contains("FINAL:")) {
                finalOutput = aiContent.substring(aiContent.indexOf("FINAL:") + 6).trim();
                finished = true;
                
                recordStep(task.getId(), stepCount, "FINAL_RESPONSE", aiContent, finalOutput, "COMPLETED");
                break;
            }

            if (aiContent.contains("TOOL:")) {
                String toolName = extractField(aiContent, "TOOL:");
                String argsJson = extractField(aiContent, "ARGS:");

                Map<String, Object> argsMap = parseArgs(argsJson);
                ToolCall toolCall = toolRegistry.executeTool(toolName, argsMap);

                String observation = "Tool [" + toolName + "] Output:\n" + toolCall.getResult();
                
                recordStep(task.getId(), stepCount, toolName, argsJson, observation, toolCall.isSuccess() ? "SUCCESS" : "FAILED");

                messages.add(AiRequest.ChatMessage.builder().role("assistant").content(aiContent).build());
                messages.add(AiRequest.ChatMessage.builder().role("user").content(observation).build());
            } else {
                // Conversational reasoning step
                recordStep(task.getId(), stepCount, "THINKING", "", aiContent, "SUCCESS");
                messages.add(AiRequest.ChatMessage.builder().role("assistant").content(aiContent).build());
                
                // If model completed prose without tool call, treat as final
                if (stepCount >= 2) {
                    finalOutput = aiContent;
                    finished = true;
                } else {
                    messages.add(AiRequest.ChatMessage.builder().role("user").content("Continue task or provide FINAL: <result>").build());
                }
            }
        }

        task.setTotalSteps(stepCount);
        if (finished) {
            task.setStatus("COMPLETED");
            task.setFinalResult(finalOutput);
            task.setCompletedAt(LocalDateTime.now());
        } else {
            task.setStatus("FAILED");
            task.setErrorDetails("Exceeded maximum step limit (" + MAX_STEPS + ")");
        }

        return agentTaskRepository.save(task);
    }

    private void recordStep(String taskId, int stepNo, String action, String input, String observation, String status) {
        AgentStep step = AgentStep.builder()
                .taskId(taskId)
                .stepNumber(stepNo)
                .actionName(action)
                .actionInput(input)
                .observation(observation)
                .status(status)
                .build();
        agentStepRepository.save(step);
    }

    private String extractField(String text, String prefix) {
        int start = text.indexOf(prefix);
        if (start == -1) return "";
        int end = text.indexOf("\n", start);
        if (end == -1) end = text.length();
        return text.substring(start + prefix.length(), end).trim();
    }

    private Map<String, Object> parseArgs(String json) {
        Map<String, Object> map = new HashMap<>();
        if (json == null || json.isEmpty()) return map;
        try {
            // Simple key-value extraction for JSON object string
            String clean = json.replaceAll("[\\{\\}\"]", "");
            String[] parts = clean.split(",");
            for (String part : parts) {
                String[] kv = part.split(":");
                if (kv.length == 2) {
                    map.put(kv[0].trim(), kv[1].trim());
                }
            }
        } catch (Exception ignored) {}
        return map;
    }
}
