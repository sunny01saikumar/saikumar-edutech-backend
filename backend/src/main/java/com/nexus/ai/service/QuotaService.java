package com.nexus.ai.service;

import com.nexus.ai.domain.TokenQuota;
import com.nexus.ai.domain.UserSubscription;
import com.nexus.ai.provider.QuotaExceededException;
import com.nexus.ai.repository.TokenQuotaRepository;
import com.nexus.ai.repository.UserSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Service
public class QuotaService {

    private static final Logger log = LoggerFactory.getLogger(QuotaService.class);

    private final TokenQuotaRepository tokenQuotaRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;

    public QuotaService(TokenQuotaRepository tokenQuotaRepository,
                        UserSubscriptionRepository userSubscriptionRepository) {
        this.tokenQuotaRepository = tokenQuotaRepository;
        this.userSubscriptionRepository = userSubscriptionRepository;
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void initDefaultUser() {
        try {
            if (userSubscriptionRepository.findByUserId("default-user").isEmpty()) {
                createDefaultSubscription("default-user");
            }
            if (tokenQuotaRepository.findByUserId("default-user").isEmpty()) {
                createDefaultQuota("default-user");
            }
            log.info("Default user subscription and token quota pre-initialized.");
        } catch (Exception e) {
            log.warn("Default user initialization warning: {}", e.getMessage());
        }
    }

    @Transactional
    public void checkAndEnforceQuota(String userId) {
        if (userId == null || userId.isEmpty()) return; // Anonymous / default user check

        UserSubscription sub = userSubscriptionRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultSubscription(userId));

        TokenQuota quota = tokenQuotaRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultQuota(userId));

        // Auto reset if date changed
        if (quota.getLastResetDate() == null || !quota.getLastResetDate().equals(LocalDate.now())) {
            quota.setTokensUsedToday(0);
            quota.setLastResetDate(LocalDate.now());
            tokenQuotaRepository.save(quota);
        }

        if ("FREE".equalsIgnoreCase(sub.getPlan()) && quota.getTokensUsedToday() >= sub.getDailyTokenLimit()) {
            log.warn("User [{}] exceeded daily token limit ({}/{})", userId, quota.getTokensUsedToday(), sub.getDailyTokenLimit());
            throw new QuotaExceededException(userId, "Daily token limit of " + sub.getDailyTokenLimit() + " tokens reached for your Free plan. It will automatically reset at 00:00 UTC, or you can upgrade to Pro for unlimited tokens.");
        }

        if ("PRO".equalsIgnoreCase(sub.getPlan()) && quota.getTokensUsedThisMonth() >= sub.getMonthlyTokenLimit()) {
            throw new QuotaExceededException(userId, "Monthly token limit of " + sub.getMonthlyTokenLimit() + " tokens reached for your Pro plan.");
        }
    }

    @Transactional
    public void deductTokens(String userId, int tokensUsed) {
        if (userId == null || userId.isEmpty() || tokensUsed <= 0) return;

        TokenQuota quota = tokenQuotaRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultQuota(userId));

        quota.setTokensUsedToday(quota.getTokensUsedToday() + tokensUsed);
        quota.setTokensUsedThisMonth(quota.getTokensUsedThisMonth() + tokensUsed);
        tokenQuotaRepository.save(quota);

        log.info("Deducted {} tokens for user [{}]. Today total: {}", tokensUsed, userId, quota.getTokensUsedToday());
    }

    @Scheduled(cron = "0 0 0 * * ?") // Midnight daily reset
    @Transactional
    public void executeDailyTokenReset() {
        log.info("Executing midnight daily token reset for all Free tier users...");
        tokenQuotaRepository.resetAllDailyTokens();
    }

    public Map<String, Object> getUsageStats(String userId) {
        String effectiveUserId = (userId != null && !userId.isEmpty()) ? userId : "default-user";

        UserSubscription sub = userSubscriptionRepository.findByUserId(effectiveUserId)
                .orElseGet(() -> createDefaultSubscription(effectiveUserId));

        TokenQuota quota = tokenQuotaRepository.findByUserId(effectiveUserId)
                .orElseGet(() -> createDefaultQuota(effectiveUserId));

        long hoursUntilReset = ChronoUnit.HOURS.between(LocalDateTime.now(), LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0));

        Map<String, Object> map = new HashMap<>();
        map.put("plan", sub.getPlan());
        map.put("tokensUsedToday", quota.getTokensUsedToday());
        map.put("dailyLimit", sub.getDailyTokenLimit());
        map.put("tokensUsedThisMonth", quota.getTokensUsedThisMonth());
        map.put("monthlyLimit", sub.getMonthlyTokenLimit());
        map.put("percentUsed", Math.min(100, (int) (((double) quota.getTokensUsedToday() / sub.getDailyTokenLimit()) * 100)));
        map.put("hoursUntilReset", Math.max(1, hoursUntilReset));
        return map;
    }

    private synchronized UserSubscription createDefaultSubscription(String userId) {
        return userSubscriptionRepository.findByUserId(userId).orElseGet(() -> {
            try {
                UserSubscription sub = UserSubscription.builder()
                        .userId(userId)
                        .plan("FREE")
                        .dailyTokenLimit(5000)
                        .monthlyTokenLimit(150000)
                        .status("ACTIVE")
                        .build();
                return userSubscriptionRepository.save(sub);
            } catch (Exception e) {
                log.warn("Concurrent subscription creation for user [{}], falling back to query", userId);
                return userSubscriptionRepository.findByUserId(userId).orElseGet(() ->
                        UserSubscription.builder().userId(userId).plan("FREE").dailyTokenLimit(5000).monthlyTokenLimit(150000).status("ACTIVE").build()
                );
            }
        });
    }

    private synchronized TokenQuota createDefaultQuota(String userId) {
        return tokenQuotaRepository.findByUserId(userId).orElseGet(() -> {
            try {
                TokenQuota quota = TokenQuota.builder()
                        .userId(userId)
                        .tokensUsedToday(0)
                        .tokensUsedThisMonth(0)
                        .lastResetDate(LocalDate.now())
                        .build();
                return tokenQuotaRepository.save(quota);
            } catch (Exception e) {
                log.warn("Concurrent quota creation for user [{}], falling back to query", userId);
                return tokenQuotaRepository.findByUserId(userId).orElseGet(() ->
                        TokenQuota.builder().userId(userId).tokensUsedToday(0).tokensUsedThisMonth(0).lastResetDate(LocalDate.now()).build()
                );
            }
        });
    }
}
