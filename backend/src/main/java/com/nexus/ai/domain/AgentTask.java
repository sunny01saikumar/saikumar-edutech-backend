package com.nexus.ai.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String goal;

    private String status; // PENDING, RUNNING, COMPLETED, FAILED

    private Integer totalSteps;

    @Column(columnDefinition = "TEXT")
    private String finalResult;

    @Column(columnDefinition = "TEXT")
    private String errorDetails;

    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
    }
}
