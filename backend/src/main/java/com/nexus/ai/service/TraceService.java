package com.nexus.ai.service;

import com.nexus.ai.domain.AuditLog;
import com.nexus.ai.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class TraceService {

    private final AuditLogRepository auditLogRepository;

    public TraceService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public String createTraceId() {
        return "trace-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public void logTrace(String traceId, String conversationId, String eventType, String provider, String model,
                         Long latencyMs, Integer promptTokens, Integer completionTokens, String status, String details) {
        AuditLog log = AuditLog.builder()
                .traceId(traceId)
                .conversationId(conversationId)
                .eventType(eventType)
                .provider(provider)
                .model(model)
                .latencyMs(latencyMs)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .status(status)
                .details(details)
                .build();
        auditLogRepository.save(log);
    }

    public List<AuditLog> getRecentTraces() {
        return auditLogRepository.findTop100ByOrderByTimestampDesc();
    }

    public List<AuditLog> getTraceDetails(String traceId) {
        return auditLogRepository.findByTraceId(traceId);
    }
}
