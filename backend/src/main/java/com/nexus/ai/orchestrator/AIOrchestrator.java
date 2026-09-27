package com.nexus.ai.orchestrator;

import com.nexus.ai.domain.*;
import com.nexus.ai.provider.AIProvider;
import com.nexus.ai.provider.AIProviderUnavailableException;
import com.nexus.ai.rag.RAGService;
import com.nexus.ai.service.ConversationService;
import com.nexus.ai.service.QuotaService;
import com.nexus.ai.service.TraceService;
import com.nexus.ai.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

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

    public AIOrchestrator(ModelRouter modelRouter,
                          ContextWindowBuilder contextWindowBuilder,
                          ConversationService conversationService,
                          RAGService ragService,
                          TraceService traceService,
                          ToolRegistry toolRegistry,
                          QuotaService quotaService) {
        this.modelRouter = modelRouter;
        this.contextWindowBuilder = contextWindowBuilder;
        this.conversationService = conversationService;
        this.ragService = ragService;
        this.traceService = traceService;
        this.toolRegistry = toolRegistry;
        this.quotaService = quotaService;
    }

    public AiResponse processChat(String prompt, String conversationId, String providerName, String modelName, String systemPrompt) {
        return processChat(prompt, conversationId, providerName, modelName, systemPrompt, null);
    }

    public AiResponse processChat(String prompt, String conversationId, String providerName, String modelName, String systemPrompt, String customApiKey) {
        long start = System.currentTimeMillis();
        String traceId = traceService.createTraceId();
        String userId = "default-user";

        // 1. Enforce Token Quota
        quotaService.checkAndEnforceQuota(userId);

        // 2. Conversation setup
        Conversation conversation = conversationService.getOrCreateConversation(conversationId, prompt);
        String activeConvId = conversation.getId();

        // 3. Save User Message
        conversationService.saveMessage(activeConvId, MessageRole.USER, prompt, null, null, null, null, null);

        // 4. Retrieve RAG Document Context
        String ragContext = ragService.retrieveRelevantContext(prompt);

        // 5. Model Routing
        ModelRouter.RouteDecision route = modelRouter.route(prompt, providerName, modelName, customApiKey);
        AIProvider provider = route.getProvider();

        if (!provider.isAvailable() && (customApiKey == null || customApiKey.trim().isEmpty())) {
            traceService.logTrace(traceId, activeConvId, "CHAT_REQUEST", provider.getProviderName(), route.getTargetModel(),
                    System.currentTimeMillis() - start, 0, 0, "FAILED", "Provider unavailable");
            throw new AIProviderUnavailableException(provider.getProviderName(), "Configured AI provider [" + provider.getProviderName() + "] is unavailable. Please enter an API key in settings.");
        }

        // 6. Build Context Messages & System Prompt
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

        // 7. Execute REAL Model Call
        AiResponse response = provider.generate(request);
        response.setConversationId(activeConvId);
        response.setTraceId(traceId);

        long latency = System.currentTimeMillis() - start;
        response.setLatencyMs(latency);

        int totalTokens = response.getUsage() != null ? response.getUsage().getTotalTokens() : 100;
        
        // 8. Deduct Tokens from User Quota
        quotaService.deductTokens(userId, totalTokens > 0 ? totalTokens : 100);

        // 9. Save Assistant Response to DB
        conversationService.saveMessage(activeConvId, MessageRole.ASSISTANT, response.getContent(),
                provider.getProviderName(), route.getTargetModel(),
                response.getUsage() != null ? response.getUsage().getPromptTokens() : 0,
                response.getUsage() != null ? response.getUsage().getCompletionTokens() : 0,
                latency);

        // 10. Log Trace
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

        // Enforce Token Quota
        quotaService.checkAndEnforceQuota(userId);

        Conversation conversation = conversationService.getOrCreateConversation(conversationId, prompt);
        String activeConvId = conversation.getId();

        conversationService.saveMessage(activeConvId, MessageRole.USER, prompt, null, null, null, null, null);

        String ragContext = ragService.retrieveRelevantContext(prompt);
        ModelRouter.RouteDecision route = modelRouter.route(prompt, providerName, modelName, customApiKey);
        AIProvider provider = route.getProvider();

        if (!provider.isAvailable() && (customApiKey == null || customApiKey.trim().isEmpty())) {
            return Flux.error(new AIProviderUnavailableException(provider.getProviderName(), "Configured AI provider [" + provider.getProviderName() + "] is unavailable. Please enter an API key in settings."));
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
                .doOnError(e -> {
                    traceService.logTrace(traceId, activeConvId, "STREAM_REQUEST", provider.getProviderName(), route.getTargetModel(),
                            0L, 0, 0, "FAILED", e.getMessage());
                });
    }
}
