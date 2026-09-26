package com.nexus.ai.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextChunker {

    private static final int DEFAULT_CHUNK_SIZE = 800;
    private static final int DEFAULT_OVERLAP = 150;

    public List<String> chunkText(String fullText) {
        if (fullText == null || fullText.trim().isEmpty()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        String text = fullText.replaceAll("\\r\\n", "\n").trim();
        int length = text.length();

        if (length <= DEFAULT_CHUNK_SIZE) {
            chunks.add(text);
            return chunks;
        }

        int start = 0;
        while (start < length) {
            int end = Math.min(start + DEFAULT_CHUNK_SIZE, length);
            
            // Try to end on sentence or paragraph boundary if possible
            if (end < length) {
                int lastPeriod = text.lastIndexOf('.', end);
                int lastNewline = text.lastIndexOf('\n', end);
                int boundary = Math.max(lastPeriod, lastNewline);
                if (boundary > start + (DEFAULT_CHUNK_SIZE / 2)) {
                    end = boundary + 1;
                }
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            start = end - DEFAULT_OVERLAP;
            if (start >= length || end == length) break;
        }

        return chunks;
    }
}
