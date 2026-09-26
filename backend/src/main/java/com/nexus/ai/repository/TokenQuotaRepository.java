package com.nexus.ai.repository;

import com.nexus.ai.domain.TokenQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TokenQuotaRepository extends JpaRepository<TokenQuota, String> {
    Optional<TokenQuota> findByUserId(String userId);

    @Modifying
    @Query("UPDATE TokenQuota t SET t.tokensUsedToday = 0, t.lastResetDate = CURRENT_DATE")
    void resetAllDailyTokens();
}
