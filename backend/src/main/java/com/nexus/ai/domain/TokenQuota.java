package com.nexus.ai.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "token_quotas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String userId;

    private Integer tokensUsedToday;
    private Integer tokensUsedThisMonth;
    private LocalDate lastResetDate;

    @PrePersist
    public void onCreate() {
        if (tokensUsedToday == null) tokensUsedToday = 0;
        if (tokensUsedThisMonth == null) tokensUsedThisMonth = 0;
        if (lastResetDate == null) lastResetDate = LocalDate.now();
    }
}
