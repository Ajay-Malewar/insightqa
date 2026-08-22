package com.ajaymalewar.insightqa.service;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;

import java.util.List;

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
}