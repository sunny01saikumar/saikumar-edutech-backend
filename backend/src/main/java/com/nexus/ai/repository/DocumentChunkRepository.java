package com.nexus.ai.repository;

import com.nexus.ai.domain.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, String> {
    List<DocumentChunk> findByDocumentId(String documentId);
    void deleteByDocumentId(String documentId);
}
