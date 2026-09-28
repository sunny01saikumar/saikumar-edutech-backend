package com.nexus.ai.controller;

import com.nexus.ai.domain.Document;
import com.nexus.ai.rag.RAGService;
import com.nexus.ai.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/documents")
public class RAGController {

    private final RAGService ragService;
    private final JwtUtil jwtUtil;

    public RAGController(RAGService ragService, JwtUtil jwtUtil) {
        this.ragService = ragService;
        this.jwtUtil = jwtUtil;
    }

    private void verifyAdminAccess(String authHeader) {
        String role = jwtUtil.extractRole(authHeader);
        if (!"ROLE_ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied: Only Admin users can manage or view RAG data.");
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<Document> uploadDocument(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                                    @RequestParam("file") MultipartFile file) throws Exception {
        verifyAdminAccess(authHeader);

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
    public List<Document> getDocuments(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        verifyAdminAccess(authHeader);
        return ragService.getAllDocuments();
    }

    @DeleteMapping("/{id}")
    public Map<String, String> deleteDocument(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                               @PathVariable String id) {
        verifyAdminAccess(authHeader);
        ragService.deleteDocument(id);
        return Map.of("status", "deleted", "id", id);
    }
}
