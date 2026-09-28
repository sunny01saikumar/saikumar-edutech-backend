package com.nexus.ai.controller;

import com.nexus.ai.domain.AuditLog;
import com.nexus.ai.security.JwtUtil;
import com.nexus.ai.service.TraceService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/ai/traces")
public class TraceController {

    private final TraceService traceService;
    private final JwtUtil jwtUtil;

    public TraceController(TraceService traceService, JwtUtil jwtUtil) {
        this.traceService = traceService;
        this.jwtUtil = jwtUtil;
    }

    private void verifyAdminAccess(String authHeader) {
        String role = jwtUtil.extractRole(authHeader);
        if (!"ROLE_ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied: Only Admin users can view observability traces.");
        }
    }

    @GetMapping
    public List<AuditLog> getTraces(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        verifyAdminAccess(authHeader);
        return traceService.getRecentTraces();
    }

    @GetMapping("/{traceId}")
    public List<AuditLog> getTraceDetails(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                           @PathVariable String traceId) {
        verifyAdminAccess(authHeader);
        return traceService.getTraceDetails(traceId);
    }
}
