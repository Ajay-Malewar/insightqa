package com.ajaymalewar.insightqa.service;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentChunkingStrategyTest {

    private static final Logger log = LoggerFactory.getLogger(DocumentChunkingStrategyTest.class);

    private final DocumentChunkingStrategy strategy = new DocumentChunkingStrategy();

    @Test
    void looksLikeQaFormat_shouldReturnTrueForNumberedQuestions() {
        String text = "1. What is Spring Boot?\n2. What is REST?\n3. What is Docker?";
        boolean result = strategy.looksLikeQaFormat(text);
        log.info("Q&A format detection for numbered questions: {}", result);

        assertTrue(result);
    }

    @Test
    void looksLikeQaFormat_shouldReturnFalseForPlainProse() {
        String text = "Employees are entitled to 12 paid sick leaves per year. Requests must be submitted in advance.";
        boolean result = strategy.looksLikeQaFormat(text);
        log.info("Q&A format detection for plain prose: {}", result);

        assertFalse(result);
    }

    @Test
    void chunkByQaPairs_shouldProduceOneChunkPerQuestion() {
        String text = "1. What is Spring Boot?\nAnswer: A framework.\n2. What is REST?\nAnswer: An architecture style.\n3. What is Docker?\nAnswer: A container platform.";
        List<Document> chunks = strategy.chunkByQaPairs(text, "test.pdf");
        log.info("Chunk count from Q&A splitting: {}", chunks.size());

        assertEquals(3, chunks.size());
        assertTrue(chunks.get(0).getText().contains("Spring Boot"));
        assertTrue(chunks.get(1).getText().contains("REST"));
        assertTrue(chunks.get(2).getText().contains("Docker"));
    }

    @Test
    void chunkByQaPairs_shouldTagEachChunkWithFilename() {
        String text = "1. What is Spring Boot?\nAnswer: A framework.";
        List<Document> chunks = strategy.chunkByQaPairs(text, "my-doc.pdf");
        log.info("Chunk metadata: {}", chunks.get(0).getMetadata());

        assertEquals("my-doc.pdf", chunks.get(0).getMetadata().get("fileName"));
    }

    @Test
    void chunkByTokens_shouldApplyOverlapBetweenAdjacentChunks() {
        // Build a long-enough text that TokenTextSplitter will split into multiple chunks.
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            longText.append("This is sentence number ").append(i).append(" in a long test document. ");
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fileName", "long-doc.txt");
        Document rawDoc = new Document(longText.toString(), metadata);

        List<Document> chunks = strategy.chunkByTokens(List.of(rawDoc));
        log.info("Produced {} chunks from long document", chunks.size());

        assertTrue(chunks.size() > 1, "Expected multiple chunks from a long document");

        // The start of chunk 2 should contain some text that also appears at the
        // end of chunk 1, proving overlap was applied.
        String endOfFirstChunk = chunks.get(0).getText()
                .substring(Math.max(0, chunks.get(0).getText().length() - 50));
        String startOfSecondChunk = chunks.get(1).getText()
                .substring(0, Math.min(150, chunks.get(1).getText().length()));

        log.info("End of chunk 1: \"{}\"", endOfFirstChunk);
        log.info("Start of chunk 2: \"{}\"", startOfSecondChunk);

        assertTrue(startOfSecondChunk.contains(endOfFirstChunk.trim().split(" ")[0]),
                "Expected overlap: start of chunk 2 should contain part of the end of chunk 1");
    }
}