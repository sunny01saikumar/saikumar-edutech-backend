package com.nexus.ai.rag;

import com.nexus.ai.domain.Document;
import com.nexus.ai.domain.DocumentChunk;
import com.nexus.ai.repository.DocumentChunkRepository;
import com.nexus.ai.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RAGService {

    private static final Logger log = LoggerFactory.getLogger(RAGService.class);

    private final DocumentParser documentParser;
    private final TextChunker textChunker;
    private final VectorService vectorService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;

    public RAGService(DocumentParser documentParser,
                      TextChunker textChunker,
                      VectorService vectorService,
                      DocumentRepository documentRepository,
                      DocumentChunkRepository documentChunkRepository) {
        this.documentParser = documentParser;
        this.textChunker = textChunker;
        this.vectorService = vectorService;
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
    }

    @Transactional
    public Document processAndIndexDocument(InputStream inputStream, String filename, String fileType, long fileSize) {
        log.info("Processing and indexing document: {} ({}, {} bytes)", filename, fileType, fileSize);
        
        String extractedText = documentParser.parseContent(inputStream, filename, fileType);
        List<String> rawChunks = textChunker.chunkText(extractedText);

        Document doc = Document.builder()
                .filename(filename)
                .fileType(fileType)
                .fileSize(fileSize)
                .totalChunks(rawChunks.size())
                .build();

        doc = documentRepository.save(doc);

        List<DocumentChunk> entityChunks = new ArrayList<>();
        for (int i = 0; i < rawChunks.size(); i++) {
            DocumentChunk chunk = DocumentChunk.builder()
                    .documentId(doc.getId())
                    .chunkIndex(i)
                    .content(rawChunks.get(i))
                    .build();
            entityChunks.add(chunk);
        }

        documentChunkRepository.saveAll(entityChunks);
        log.info("Indexed {} chunks for document {}", entityChunks.size(), doc.getId());

        return doc;
    }

    public String retrieveRelevantContext(String query) {
        if (query == null || query.trim().isEmpty()) return "";

        List<DocumentChunk> allChunks = documentChunkRepository.findAll();
        if (allChunks.isEmpty()) return "";

        List<DocumentChunk> topChunks = vectorService.findTopK(query, allChunks, 4);
        if (topChunks.isEmpty()) return "";

        return topChunks.stream()
                .map(c -> "[Chunk " + c.getChunkIndex() + "]: " + c.getContent())
                .collect(Collectors.joining("\n\n"));
    }

    public List<Document> getAllDocuments() {
        return documentRepository.findAllByOrderByUploadedAtDesc();
    }

    @Transactional
    public void deleteDocument(String id) {
        documentChunkRepository.deleteByDocumentId(id);
        documentRepository.deleteById(id);
    }
}
