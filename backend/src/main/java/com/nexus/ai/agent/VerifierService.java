package com.nexus.ai.agent;

import com.nexus.ai.tools.CodeExecutorTool;
import com.nexus.ai.domain.ToolCall;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class VerifierService {

    private final CodeExecutorTool codeExecutorTool;

    public VerifierService(CodeExecutorTool codeExecutorTool) {
        this.codeExecutorTool = codeExecutorTool;
    }

    public static class VerificationResult {
        private final boolean success;
        private final String details;

        public VerificationResult(boolean success, String details) {
            this.success = success;
            this.details = details;
        }

        public boolean isSuccess() { return success; }
        public String getDetails() { return details; }
    }

    public VerificationResult verifyBuildOrTest(String command, String workingDirectory) {
        ToolCall call = codeExecutorTool.execute(Map.of(
                "command", command,
                "workingDirectory", workingDirectory != null ? workingDirectory : "."
        ));

        return new VerificationResult(call.isSuccess(), call.getResult());
    }
}
