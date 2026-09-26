package com.nexus.ai.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "document_chunks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String documentId;

    private Integer chunkIndex;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private String keywords;
}
