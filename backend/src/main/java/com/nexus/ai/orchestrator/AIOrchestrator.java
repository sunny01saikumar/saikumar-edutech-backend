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

    public AIOrchestrator(ModelRouter modelRouter,
                          ContextWindowBuilder contextWindowBuilder,
                          ConversationService conversationService,
                          RAGService ragService,
                          TraceService traceService,
                          ToolRegistry toolRegistry,
                          QuotaService quotaService,
                          com.nexus.ai.orchestrator.QueryIntentParser queryIntentParser,
                          com.nexus.ai.security.SecurityShield securityShield) {
        this.modelRouter = modelRouter;
        this.contextWindowBuilder = contextWindowBuilder;
        this.conversationService = conversationService;
        this.ragService = ragService;
        this.traceService = traceService;
        this.toolRegistry = toolRegistry;
        this.quotaService = quotaService;
        this.queryIntentParser = queryIntentParser;
        this.securityShield = securityShield;
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
                    // Extract previous assistant or user message subject to resolve pronouns
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

                            // Append live web search data to immediate context window (RAG vector store is reserved for explicit admin uploads)
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

    public AiResponse processChat(String prompt, String conversationId, String providerName, String modelName, String systemPrompt) {
        return processChat(prompt, conversationId, providerName, modelName, systemPrompt, null);
    }

    public AiResponse processChat(String prompt, String conversationId, String providerName, String modelName, String systemPrompt, String customApiKey) {
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
        return processStreamingChat(prompt, conversationId, providerName, modelName, systemPrompt, null);
    }

    public Flux<String> processStreamingChat(String prompt, String conversationId, String providerName, String modelName, String systemPrompt, String customApiKey) {
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
