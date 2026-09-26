package com.nexus.ai.rag;

import com.nexus.ai.domain.DocumentChunk;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class VectorService {

    public List<DocumentChunk> findTopK(String query, List<DocumentChunk> chunks, int topK) {
        if (query == null || query.trim().isEmpty() || chunks.isEmpty()) {
            return List.of();
        }

        Set<String> queryTerms = tokenize(query);

        Map<DocumentChunk, Double> scores = new HashMap<>();

        for (DocumentChunk chunk : chunks) {
            Set<String> chunkTerms = tokenize(chunk.getContent());
            double score = calculateJaccardCosineSimilarity(queryTerms, chunkTerms);
            if (score > 0.0) {
                scores.put(chunk, score);
            }
        }

        return scores.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private double calculateJaccardCosineSimilarity(Set<String> queryTerms, Set<String> chunkTerms) {
        if (queryTerms.isEmpty() || chunkTerms.isEmpty()) return 0.0;

        long intersectionCount = queryTerms.stream().filter(chunkTerms::contains).count();
        if (intersectionCount == 0) return 0.0;

        double unionCount = queryTerms.size() + chunkTerms.size() - intersectionCount;
        return intersectionCount / unionCount;
    }

    private Set<String> tokenize(String text) {
        if (text == null) return Set.of();
        String[] tokens = text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").split("\\s+");
        Set<String> set = new HashSet<>();
        for (String t : tokens) {
            if (t.length() > 2) {
                set.add(t);
            }
        }
        return set;
    }
}
