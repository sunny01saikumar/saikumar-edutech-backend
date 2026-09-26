package com.nexus.ai.agent;

import com.nexus.ai.domain.AgentTask;
import com.nexus.ai.tools.CodeExecutorTool;
import com.nexus.ai.tools.FileReaderTool;
import com.nexus.ai.domain.ToolCall;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CodingAgent {

    private final AgentLoopEngine agentLoopEngine;
    private final CodeExecutorTool codeExecutorTool;
    private final FileReaderTool fileReaderTool;

    public CodingAgent(AgentLoopEngine agentLoopEngine,
                       CodeExecutorTool codeExecutorTool,
                       FileReaderTool fileReaderTool) {
        this.agentLoopEngine = agentLoopEngine;
        this.codeExecutorTool = codeExecutorTool;
        this.fileReaderTool = fileReaderTool;
    }

    public AgentTask executeCodingTask(String goal, String verificationCommand, String provider, String model) {
        String fullGoal = "CODING TASK: " + goal;
        if (verificationCommand != null && !verificationCommand.isEmpty()) {
            fullGoal += " [Verify using command: " + verificationCommand + "]";
        }
        return agentLoopEngine.executeTaskLoop(fullGoal, provider, model);
    }
}
