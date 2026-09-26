package com.nexus.ai.controller;

import com.nexus.ai.domain.UserSubscription;
import com.nexus.ai.repository.UserSubscriptionRepository;
import com.nexus.ai.security.JwtUtil;
import com.nexus.ai.service.QuotaService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

    private final QuotaService quotaService;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final JwtUtil jwtUtil;

    public SubscriptionController(QuotaService quotaService,
                                  UserSubscriptionRepository userSubscriptionRepository,
                                  JwtUtil jwtUtil) {
        this.quotaService = quotaService;
        this.userSubscriptionRepository = userSubscriptionRepository;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/plans")
    public List<Map<String, Object>> getPlans() {
        return List.of(
                Map.of(
                        "id", "FREE",
                        "name", "Free Plan",
                        "price", "$0",
                        "period", "forever",
                        "dailyTokens", 5000,
                        "features", List.of("5,000 Tokens Daily (Auto Resets)", "Access to All Basic Models", "Real-Time SSE Streaming", "Standard Web Search & Calculator")
                ),
                Map.of(
                        "id", "PRO",
                        "name", "Pro SaaS Plan",
                        "price", "$19",
                        "period", "month",
                        "dailyTokens", 500000,
                        "features", List.of("500,000 Monthly Tokens", "Priority Model Access (GPT-4o, Claude 3.5)", "RAG Document Indexing (50 Files)", "Autonomous Coding Agent Access", "00:00 Daily Quota Reset")
                ),
                Map.of(
                        "id", "ENTERPRISE",
                        "name", "Enterprise Plan",
                        "price", "$99",
                        "period", "month",
                        "dailyTokens", -1,
                        "features", List.of("Unlimited Tokens", "Custom LLM Fine-Tuning & Local Ollama", "Unlimited RAG Storage", "Dedicated API Key Access", "24/7 Priority Support")
                )
        );
    }

    @GetMapping("/usage")
    public Map<String, Object> getUsage(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = extractUserIdFromHeader(authHeader);
        return quotaService.getUsageStats(userId);
    }

    @PostMapping("/upgrade")
    public Map<String, Object> upgradePlan(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                           @RequestBody Map<String, String> body) {
        String userId = extractUserIdFromHeader(authHeader);
        String newPlan = body.getOrDefault("plan", "PRO").toUpperCase();

        UserSubscription sub = userSubscriptionRepository.findByUserId(userId)
                .orElseGet(() -> UserSubscription.builder().userId(userId).build());

        sub.setPlan(newPlan);
        if ("PRO".equals(newPlan)) {
            sub.setDailyTokenLimit(50000);
            sub.setMonthlyTokenLimit(500000);
        } else if ("ENTERPRISE".equals(newPlan)) {
            sub.setDailyTokenLimit(9999999);
            sub.setMonthlyTokenLimit(99999999);
        }
        userSubscriptionRepository.save(sub);

        return Map.of("status", "success", "plan", newPlan, "message", "Successfully upgraded to " + newPlan + " Plan!");
    }

    private String extractUserIdFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            String userId = jwtUtil.extractUserId(token);
            if (userId != null) return userId;
        }
        return "default-user";
    }
}
