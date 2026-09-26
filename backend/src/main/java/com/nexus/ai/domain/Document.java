package com.nexus.ai.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String filename;

    private String fileType;

    private Long fileSize;

    private Integer totalChunks;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    public void onCreate() {
        if (uploadedAt == null) uploadedAt = LocalDateTime.now();
    }
}
