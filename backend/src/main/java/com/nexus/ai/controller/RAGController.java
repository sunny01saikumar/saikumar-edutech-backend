package com.nexus.ai.controller;

import com.nexus.ai.domain.Document;
import com.nexus.ai.rag.RAGService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/documents")
public class RAGController {

    private final RAGService ragService;

    public RAGController(RAGService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Document> uploadDocument(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        Document doc = ragService.processAndIndexDocument(
                file.getInputStream(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize()
        );

        return ResponseEntity.ok(doc);
    }

    @GetMapping
    public List<Document> getDocuments() {
        return ragService.getAllDocuments();
    }

    @DeleteMapping("/{id}")
    public Map<String, String> deleteDocument(@PathVariable String id) {
        ragService.deleteDocument(id);
        return Map.of("status", "deleted", "id", id);
    }
}
