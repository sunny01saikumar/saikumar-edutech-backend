package com.nexus.ai.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String userId;

    @Column(nullable = false)
    private String plan; // FREE, PRO, ENTERPRISE

    private Integer dailyTokenLimit;
    private Integer monthlyTokenLimit;

    private String status; // ACTIVE, CANCELED

    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;

    @PrePersist
    public void onCreate() {
        if (plan == null) plan = "FREE";
        if (dailyTokenLimit == null) dailyTokenLimit = 5000;
        if (monthlyTokenLimit == null) monthlyTokenLimit = 150000;
        if (status == null) status = "ACTIVE";
        if (currentPeriodStart == null) currentPeriodStart = LocalDateTime.now();
    }
}
