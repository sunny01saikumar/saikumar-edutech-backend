package com.nexus.ai.repository;

import com.nexus.ai.domain.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, String> {
    Optional<UserSubscription> findByUserId(String userId);
}
