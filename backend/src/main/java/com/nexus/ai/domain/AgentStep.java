package com.nexus.ai.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_steps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String taskId;

    private Integer stepNumber;

    private String actionName;

    @Column(columnDefinition = "TEXT")
    private String actionInput;

    @Column(columnDefinition = "TEXT")
    private String observation;

    private String status;

    private LocalDateTime timestamp;

    @PrePersist
    public void onCreate() {
        if (timestamp == null) timestamp = LocalDateTime.now();
    }
}
