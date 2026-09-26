package com.nexus.ai.controller;

import com.nexus.ai.domain.AuditLog;
import com.nexus.ai.service.TraceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai/traces")
public class TraceController {

    private final TraceService traceService;

    public TraceController(TraceService traceService) {
        this.traceService = traceService;
    }

    @GetMapping
    public List<AuditLog> getTraces() {
        return traceService.getRecentTraces();
    }

    @GetMapping("/{traceId}")
    public List<AuditLog> getTraceDetails(@PathVariable String traceId) {
        return traceService.getTraceDetails(traceId);
    }
}
