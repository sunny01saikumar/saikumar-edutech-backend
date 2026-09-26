package com.nexus.ai.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String traceId;
    private String conversationId;
    private String eventType;
    private String provider;
    private String model;
    private Long latencyMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private String status;

    @Column(columnDefinition = "TEXT")
    private String details;

    private LocalDateTime timestamp;

    @PrePersist
    public void onCreate() {
        if (timestamp == null) timestamp = LocalDateTime.now();
    }
}
