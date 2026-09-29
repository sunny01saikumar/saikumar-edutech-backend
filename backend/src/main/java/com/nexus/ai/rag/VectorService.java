package com.nexus.ai.rag;

import com.nexus.ai.domain.DocumentChunk;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class VectorService {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "is", "at", "which", "on", "and", "a", "an", "in", "to", "for", "of", "or", "by",
            "with", "this", "that", "it", "from", "as", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "but", "not", "what", "where", "when", "how",
            "who", "why", "can", "could", "would", "should", "my", "your", "his", "her", "its",
            "our", "their", "here", "there", "about", "into", "through", "during", "before", "after",
            "above", "below", "up", "down", "out", "off", "over", "under", "again", "further", "then", "once"
    );

    public List<DocumentChunk> findTopK(String query, List<DocumentChunk> chunks, int topK) {
        if (query == null || query.trim().isEmpty() || chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        Set<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) {
            return List.of();
        }

        Map<DocumentChunk, Double> scores = new HashMap<>();

        for (DocumentChunk chunk : chunks) {
            Set<String> chunkTerms = tokenize(chunk.getContent());
            double score = calculateJaccardCosineSimilarity(queryTerms, chunkTerms);
            if (score >= 0.15) { // Require minimum 15% keyword overlap after stop word filtering
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
            if (t.length() > 2 && !STOP_WORDS.contains(t)) {
                set.add(t);
            }
        }
        return set;
    }
}

