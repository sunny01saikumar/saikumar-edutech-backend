package com.nexus.ai.orchestrator;

import com.nexus.ai.domain.*;
import com.nexus.ai.provider.AIProvider;
import com.nexus.ai.provider.AIProviderUnavailableException;
import com.nexus.ai.rag.RAGService;
import com.nexus.ai.service.ConversationService;
import com.nexus.ai.service.QuotaService;
import com.nexus.ai.service.TraceService;
import com.nexus.ai.tools.Tool;
import com.nexus.ai.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AIOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AIOrchestrator.class);

    private final ModelRouter modelRouter;
    private final ContextWindowBuilder contextWindowBuilder;
    private final ConversationService conversationService;
    private final RAGService ragService;
    private final TraceService traceService;
    private final ToolRegistry toolRegistry;
    private final QuotaService quotaService;
    private final com.nexus.ai.orchestrator.QueryIntentParser queryIntentParser;
    private final com.nexus.ai.security.SecurityShield securityShield;
    private final com.nexus.ai.security.JwtUtil jwtUtil;

    public AIOrchestrator(ModelRouter modelRouter,
                          ContextWindowBuilder contextWindowBuilder,
                          ConversationService conversationService,
                          RAGService ragService,
                          TraceService traceService,
                          ToolRegistry toolRegistry,
                          QuotaService quotaService,
                          com.nexus.ai.orchestrator.QueryIntentParser queryIntentParser,
                          com.nexus.ai.security.SecurityShield securityShield,
                          com.nexus.ai.security.JwtUtil jwtUtil) {
        this.modelRouter = modelRouter;
        this.contextWindowBuilder = contextWindowBuilder;
        this.conversationService = conversationService;
        this.ragService = ragService;
        this.traceService = traceService;
        this.toolRegistry = toolRegistry;
        this.quotaService = quotaService;
        this.queryIntentParser = queryIntentParser;
        this.securityShield = securityShield;
        this.jwtUtil = jwtUtil;
    }

    private String gatherAndLearnContext(String prompt, String conversationId) {
        StringBuilder combinedContext = new StringBuilder();

        // 0. Security Shield Prompt Sanitization
        String safePrompt = securityShield.sanitizePrompt(prompt);

        // 1. Resolve Multi-Turn Pronouns ONLY when explicit pronouns exist in prompt
        String resolvedSearchPrompt = safePrompt;
        String lowerPrompt = safePrompt.toLowerCase();
        boolean hasPronoun = lowerPrompt.matches(".*\\b(he|him|his|she|her|it|they|them|this|that|himself|herself)\\b.*");

        if (hasPronoun && conversationId != null && !conversationId.isEmpty()) {
            try {
                List<Message> history = conversationService.getMessagesForConversation(conversationId);
                if (history != null && history.size() > 1) {
                    for (int i = history.size() - 2; i >= 0; i--) {
                        Message prevMsg = history.get(i);
                        if (prevMsg.getContent() != null && !prevMsg.getContent().trim().isEmpty()) {
                            String prevText = prevMsg.getContent().replaceAll("[\\r\\n]", " ").trim();
                            int periodIdx = prevText.indexOf('.');
                            if (periodIdx > 10 && periodIdx < 80) {
                                prevText = prevText.substring(0, periodIdx);
                            } else if (prevText.length() > 80) {
                                prevText = prevText.substring(0, 80);
                            }
                            resolvedSearchPrompt = safePrompt + " " + prevText;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Multi-turn context resolution warning: {}", e.getMessage());
            }
        }

        // 2. Retrieve Existing RAG Vector Memory
        String ragContext = ragService.retrieveRelevantContext(resolvedSearchPrompt);
        if (ragContext != null && !ragContext.trim().isEmpty()) {
            combinedContext.append(ragContext).append("\n\n");
        }

        // 3. Intent Parsing & Domain-Targeted Multi-Platform Search Routing
        try {
            com.nexus.ai.orchestrator.QueryIntentParser.ParsedIntent intent = queryIntentParser.parse(resolvedSearchPrompt);
            Tool webTool = toolRegistry.getTool("web_search");
            if (webTool != null && intent != null && !intent.getTargetedSearchQueries().isEmpty()) {
                for (String searchQuery : intent.getTargetedSearchQueries()) {
                    ToolCall toolCall = webTool.execute(Map.of("query", searchQuery));
                    if (toolCall != null && toolCall.isSuccess() && toolCall.getResult() != null) {
                        String searchResult = toolCall.getResult();
                        if (!searchResult.contains("No direct web search results found")) {
                            combinedContext.append("--- TARGETED MULTI-SOURCE SEARCH DATA (Domain: ")
                                    .append(intent.getDomain()).append(", Tag: ").append(intent.getQuestionTag()).append(") ---\n")
                                    .append(searchResult)
                                    .append("\n--------------------------------------\n");
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Autonomous domain web search & self-learning warning: {}", e.getMessage());
        }

        return combinedContext.toString().trim();
    }

    private boolean isCodingOrDeployIntent(String prompt) {
        if (prompt == null) return false;
        String lower = prompt.toLowerCase();
        return lower.contains("code") || lower.contains("fix") || lower.contains("deploy") ||
               lower.contains("push") || lower.contains("git") || lower.contains("build") ||
               lower.contains("refactor") || lower.contains("analysis") || lower.contains("file") ||
               lower.contains("create") || lower.contains("java") || lower.contains("spring") ||
               lower.contains("endpoint") || lower.contains("c:\\") || lower.contains("github") ||
               lower.contains("disappearing") || lower.contains("repository") || lower.contains("nexus");
    }

    private Flux<String> executeAutonomousChatLoop(String prompt, String activeConvId, String traceId,
                                                   AIProvider provider, ModelRouter.RouteDecision route,
                                                   String customApiKey, String userEmail, String ragContext) {
        log.info("[Trace {}] Starting Autonomous Coding Agent Loop for logged-in user [{}] in main chat", traceId, userEmail);

        return Flux.create(emitter -> {
            try {
                List<AiRequest.ChatMessage> loopMessages = new ArrayList<>();
                String agentSystemPrompt = """
                        You are NEXUS Autonomous AI Senior Systems Architect & Lead Software Engineer.
                        You are executing an autonomous coding, codebase file editing, git release, or deployment task directly in main chat for authenticated user: %s.

                        AVAILABLE TOOLS YOU CAN INVOKE:
                        1. TOOL: file_reader
                           ARGS: {"filePath": "./"} or {"filePath": "backend/src/main/resources/application.yml"}
                        2. TOOL: file_writer
                           ARGS: {"filePath": "path/to/file", "content": "complete updated file content"}
                        3. TOOL: code_executor
                           ARGS: {"command": "mvnw.cmd test" or "npm run build"}
                        4. TOOL: git
                           ARGS: {"subcommand": "status" or "log -n 5"}
                        5. TOOL: deploy
                           ARGS: {"target": "git_push" or "maven_build" or "deploy_webhook"}

                        HOW TO INVOKE A TOOL:
                        To use any tool, output EXACTLY:
                        TOOL: <tool_name>
                        ARGS: {"arg1": "val1"}

                        CRITICAL ACTION MANDATE:
                        - DO NOT output conversational questions asking "What feature would you like me to add?".
                        - EVEN IF THE USER PROMPT IS GENERAL (e.g. "update the code", "code", "analyze codebase"), IMMEDIATELY START BY INVOKING TOOL `file_reader` WITH ARGS `{"filePath": "./"}` TO INSPECT THE PROJECT WORKSPACE AND AUDIT/UPDATE FILES!

                        WHEN FINISHED:
                        When all code edits, builds, git pushes, or analyses are completed, summarize your work clearly and end with:
                        FINAL: <summary of final result>
                        """.formatted(userEmail);

                if (ragContext != null && !ragContext.trim().isEmpty()) {
                    agentSystemPrompt += "\n\n--- RETRIEVED KNOWLEDGE ---\n" + ragContext;
                }

                loopMessages.add(AiRequest.ChatMessage.builder().role("system").content(agentSystemPrompt).build());
                loopMessages.add(AiRequest.ChatMessage.builder().role("user").content(prompt).build());

                int maxSteps = 6;
                int step = 0;
                boolean finished = false;
                StringBuilder fullTranscript = new StringBuilder();

                while (step < maxSteps && !finished && !emitter.isCancelled()) {
                    step++;
                    AiRequest request = AiRequest.builder()
                            .conversationId(activeConvId)
                            .systemPrompt(agentSystemPrompt)
                            .messages(new ArrayList<>(loopMessages))
                            .requestedProvider(provider.getProviderName())
                            .requestedModel(route.getTargetModel())
                            .customApiKey(customApiKey)
                            .maxTokens(4096)
                            .build();

                    AiResponse response = provider.generate(request);
                    String content = response.getContent() != null ? response.getContent().trim() : "";

                    if (content.contains("TOOL:")) {
                        String toolName = extractField(content, "TOOL:");
                        String argsJson = extractField(content, "ARGS:");

                        String toolHeader = "\n\n🛠️ **[NEXUS CODING AGENT - STEP " + step + "]**: Executing `" + toolName + "`...\n";
                        emitter.next(toolHeader);
                        fullTranscript.append(toolHeader);

                        Map<String, Object> argsMap = parseArgs(argsJson);
                        ToolCall toolCall = toolRegistry.executeTool(toolName, argsMap);

                        String toolOutput = "```\n" + (toolCall != null ? toolCall.getResult() : "Tool executed successfully") + "\n```\n\n";
                        emitter.next(toolOutput);
                        fullTranscript.append(toolOutput);

                        loopMessages.add(AiRequest.ChatMessage.builder().role("assistant").content(content).build());
                        loopMessages.add(AiRequest.ChatMessage.builder().role("user").content("Tool output:\n" + toolOutput + "\nNow proceed with the next tool step or output FINAL: <summary>").build());
                    } else if (content.contains("FINAL:")) {
                        String finalSummary = content.substring(content.indexOf("FINAL:") + 6).trim();
                        emitter.next(finalSummary);
                        fullTranscript.append(finalSummary);
                        finished = true;
                    } else {
                        // Model generated reasoning prose without TOOL: or FINAL:
                        if (step < 3) {
                            String toolHeader = "\n\n🛠️ **[NEXUS CODING AGENT - STEP " + step + "]**: Executing `file_reader`...\n";
                            emitter.next(toolHeader);
                            fullTranscript.append(toolHeader);

                            ToolCall toolCall = toolRegistry.executeTool("file_reader", Map.of("filePath", "./"));
                            String toolOutput = "```\n" + (toolCall != null ? toolCall.getResult() : "Directory inspected") + "\n```\n\n";
                            emitter.next(toolOutput);
                            fullTranscript.append(toolOutput);

                            loopMessages.add(AiRequest.ChatMessage.builder().role("assistant").content(content).build());
                            loopMessages.add(AiRequest.ChatMessage.builder().role("user").content("Workspace directory inspection result:\n" + toolOutput + "\nNow perform necessary code file edits using TOOL: file_writer or build commands using TOOL: code_executor.").build());
                        } else {
                            emitter.next(content);
                            fullTranscript.append(content);
                            finished = true;
                        }
                    }
                }

                conversationService.saveMessage(activeConvId, MessageRole.ASSISTANT, fullTranscript.toString(),
                        provider.getProviderName(), route.getTargetModel(), 0, 0, 0L);
                emitter.complete();
            } catch (Exception e) {
                log.error("Autonomous agent chat loop failed: {}", e.getMessage(), e);
                emitter.next("⚠️ NEXUS Coding Agent Error: " + e.getMessage());
                emitter.complete();
            }
        });
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

    public AiResponse processChat(String prompt, String conversationId, String providerName, String modelName, String systemPrompt) {
        return processChat(prompt, conversationId, providerName, modelName, systemPrompt, null, null);
    }

    public AiResponse processChat(String prompt, String conversationId, String providerName, String modelName, String systemPrompt, String customApiKey) {
        return processChat(prompt, conversationId, providerName, modelName, systemPrompt, customApiKey, null);
    }

    public AiResponse processChat(String prompt, String conversationId, String providerName, String modelName, String systemPrompt, String customApiKey, String authHeader) {
        long start = System.currentTimeMillis();
        String traceId = traceService.createTraceId();
        String userId = "default-user";

        quotaService.checkAndEnforceQuota(userId);

        Conversation conversation = conversationService.getOrCreateConversation(conversationId, prompt);
        String activeConvId = conversation.getId();

        conversationService.saveMessage(activeConvId, MessageRole.USER, prompt, null, null, null, null, null);

        String ragContext = gatherAndLearnContext(prompt, activeConvId);

        ModelRouter.RouteDecision route = modelRouter.route(prompt, providerName, modelName, customApiKey);
        AIProvider provider = route.getProvider();

        if (!provider.isAvailable() && (customApiKey == null || customApiKey.trim().isEmpty())) {
            traceService.logTrace(traceId, activeConvId, "CHAT_REQUEST", provider.getProviderName(), route.getTargetModel(),
                    System.currentTimeMillis() - start, 0, 0, "FAILED", "Provider unavailable");
            throw new AIProviderUnavailableException(provider.getProviderName(), "Configured AI provider [" + provider.getProviderName() + "] is unavailable. Please enter an API key in settings.");
        }

        String fullSystemPrompt = contextWindowBuilder.buildSystemPrompt(systemPrompt, ragContext);
        List<AiRequest.ChatMessage> messages = contextWindowBuilder.buildContextMessages(activeConvId, prompt, fullSystemPrompt, ragContext);

        AiRequest request = AiRequest.builder()
                .conversationId(activeConvId)
                .systemPrompt(fullSystemPrompt)
                .messages(messages)
                .requestedProvider(provider.getProviderName())
                .requestedModel(route.getTargetModel())
                .customApiKey(customApiKey)
                .maxTokens(4096)
                .build();

        log.info("[Trace {}] Routing prompt to provider [{}] model [{}]", traceId, provider.getProviderName(), route.getTargetModel());

        AiResponse response = provider.generate(request);
        response.setConversationId(activeConvId);
        response.setTraceId(traceId);

        long latency = System.currentTimeMillis() - start;
        response.setLatencyMs(latency);

        int totalTokens = response.getUsage() != null ? response.getUsage().getTotalTokens() : 100;
        quotaService.deductTokens(userId, totalTokens > 0 ? totalTokens : 100);

        conversationService.saveMessage(activeConvId, MessageRole.ASSISTANT, response.getContent(),
                provider.getProviderName(), route.getTargetModel(),
                response.getUsage() != null ? response.getUsage().getPromptTokens() : 0,
                response.getUsage() != null ? response.getUsage().getCompletionTokens() : 0,
                latency);

        traceService.logTrace(traceId, activeConvId, "CHAT_REQUEST", provider.getProviderName(), route.getTargetModel(),
                latency, response.getUsage() != null ? response.getUsage().getPromptTokens() : 0,
                response.getUsage() != null ? response.getUsage().getCompletionTokens() : 0, "SUCCESS", "Response generated successfully");

        return response;
    }

    public Flux<String> processStreamingChat(String prompt, String conversationId, String providerName, String modelName, String systemPrompt) {
        return processStreamingChat(prompt, conversationId, providerName, modelName, systemPrompt, null, null);
    }

    public Flux<String> processStreamingChat(String prompt, String conversationId, String providerName, String modelName, String systemPrompt, String customApiKey) {
        return processStreamingChat(prompt, conversationId, providerName, modelName, systemPrompt, customApiKey, null);
    }

    public Flux<String> processStreamingChat(String prompt, String conversationId, String providerName, String modelName, String systemPrompt, String customApiKey, String authHeader) {
        String traceId = traceService.createTraceId();
        String userId = "default-user";

        quotaService.checkAndEnforceQuota(userId);

        Conversation conversation = conversationService.getOrCreateConversation(conversationId, prompt);
        String activeConvId = conversation.getId();

        conversationService.saveMessage(activeConvId, MessageRole.USER, prompt, null, null, null, null, null);

        String ragContext = gatherAndLearnContext(prompt, activeConvId);
        ModelRouter.RouteDecision route = modelRouter.route(prompt, providerName, modelName, customApiKey);
        AIProvider provider = route.getProvider();

        if (!provider.isAvailable() && (customApiKey == null || customApiKey.trim().isEmpty())) {
            String alertMsg = "⚠️ NEXUS AI Provider Alert: Configured AI provider [" + provider.getProviderName() + "] is currently unavailable or unconfigured on the server. Please add GROQ_API_KEY or GEMINI_API_KEY in Render Environment Variables or enter a custom key in Settings.";
            conversationService.saveMessage(activeConvId, MessageRole.ASSISTANT, alertMsg, provider.getProviderName(), route.getTargetModel(), 0, 0, 0L);
            return Flux.just(alertMsg);
        }

        // Check Auth Status
        String userEmail = jwtUtil != null ? jwtUtil.extractEmail(authHeader) : null;
        boolean isAuthenticated = userEmail != null && !userEmail.trim().isEmpty();

        // Check if prompt is a Coding / File Editing / Git / Deployment Intent
        boolean isCodingTask = isCodingOrDeployIntent(prompt);

        if (isCodingTask) {
            if (!isAuthenticated) {
                String authRestrictedMsg = "🔒 **NEXUS Coding Agent Security Directive**:\n\nAutonomous Coding Agent capabilities (inspecting workspace files, editing source code, running build checks, executing Git commits, and triggering production deployments) are **restricted exclusively to logged-in users**.\n\nPlease log in to your NEXUS AI account to execute autonomous coding tasks directly in main chat!";
                conversationService.saveMessage(activeConvId, MessageRole.ASSISTANT, authRestrictedMsg, provider.getProviderName(), route.getTargetModel(), 0, 0, 0L);
                return Flux.just(authRestrictedMsg);
            } else {
                // Execute Autonomous Coding Agent Loop Directly in Main Chat!
                return executeAutonomousChatLoop(prompt, activeConvId, traceId, provider, route, customApiKey, userEmail, ragContext);
            }
        }

        String fullSystemPrompt = contextWindowBuilder.buildSystemPrompt(systemPrompt, ragContext);
        List<AiRequest.ChatMessage> messages = contextWindowBuilder.buildContextMessages(activeConvId, prompt, fullSystemPrompt, ragContext);

        AiRequest request = AiRequest.builder()
                .conversationId(activeConvId)
                .systemPrompt(fullSystemPrompt)
                .messages(messages)
                .requestedProvider(provider.getProviderName())
                .requestedModel(route.getTargetModel())
                .customApiKey(customApiKey)
                .maxTokens(4096)
                .stream(true)
                .build();

        StringBuilder fullAccumulatedText = new StringBuilder();

        return provider.stream(request)
                .doOnNext(fullAccumulatedText::append)
                .doOnComplete(() -> {
                    int estimatedTokens = Math.max(20, fullAccumulatedText.length() / 4);
                    quotaService.deductTokens(userId, estimatedTokens);

                    conversationService.saveMessage(activeConvId, MessageRole.ASSISTANT, fullAccumulatedText.toString(),
                            provider.getProviderName(), route.getTargetModel(), 0, 0, 0L);
                    traceService.logTrace(traceId, activeConvId, "STREAM_REQUEST", provider.getProviderName(), route.getTargetModel(),
                            0L, 0, 0, "SUCCESS", "Stream completed");
                })
                .onErrorResume(e -> {
                    log.error("Streaming error from provider [{}]: {}", provider.getProviderName(), e.getMessage());
                    traceService.logTrace(traceId, activeConvId, "STREAM_REQUEST", provider.getProviderName(), route.getTargetModel(),
                            0L, 0, 0, "FAILED", e.getMessage());
                    String errorMsg = "⚠️ NEXUS AI Notice: Failed to generate streaming response from provider [" + provider.getProviderName() + "]. " +
                            (e.getMessage() != null ? e.getMessage() : "Connection failed. Please check your API key settings.");
                    conversationService.saveMessage(activeConvId, MessageRole.ASSISTANT, errorMsg, provider.getProviderName(), route.getTargetModel(), 0, 0, 0L);
                    return Flux.just(errorMsg);
                });
    }
}
