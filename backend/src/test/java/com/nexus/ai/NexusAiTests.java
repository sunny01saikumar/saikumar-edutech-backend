package com.nexus.ai;

import com.nexus.ai.domain.DocumentChunk;
import com.nexus.ai.orchestrator.ContextWindowBuilder;
import com.nexus.ai.provider.OpenAIProvider;
import com.nexus.ai.provider.ProviderRegistry;
import com.nexus.ai.rag.TextChunker;
import com.nexus.ai.rag.VectorService;
import com.nexus.ai.tools.CalculatorTool;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver"
})
class NexusAiTests {

    @Autowired
    private ProviderRegistry providerRegistry;

    @Autowired
    private CalculatorTool calculatorTool;

    @Autowired
    private TextChunker textChunker;

    @Autowired
    private VectorService vectorService;

    @Autowired
    private ContextWindowBuilder contextWindowBuilder;

    @Test
    void testUnconfiguredProviderThrowsException() {
        OpenAIProvider openAIProvider = new OpenAIProvider(null, null);
        assertFalse(openAIProvider.isAvailable());
    }

    @Test
    void testCalculatorToolExecution() {
        var call = calculatorTool.execute(Map.of("expression", "123456 * 98765"));
        assertTrue(call.isSuccess());
        assertEquals("1.219313184E10", call.getResult());
    }

    @Test
    void testTextChunkerAndVectorSearch() {
        String sampleText = "Java is a popular programming language created by James Gosling in 1995. HashMap is an internal data structure in Java.";
        List<String> chunks = textChunker.chunkText(sampleText);
        assertFalse(chunks.isEmpty());

        DocumentChunk docChunk = DocumentChunk.builder()
                .documentId("doc-1")
                .chunkIndex(0)
                .content(sampleText)
                .build();

        List<DocumentChunk> results = vectorService.findTopK("HashMap internal java", List.of(docChunk), 1);
        assertFalse(results.isEmpty());
        assertEquals("doc-1", results.get(0).getDocumentId());
    }

    @Test
    void testContextWindowBuilder() {
        String systemPrompt = contextWindowBuilder.buildSystemPrompt("Custom Prompt", "Retrieved document text");
        assertTrue(systemPrompt.contains("Custom Prompt"));
        assertTrue(systemPrompt.contains("Retrieved document text"));
    }
}
