package com.ajaymalewar.insightqa.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class DocumentChunkingStrategy {

    private static final Pattern QA_PATTERN = Pattern.compile(
            "(\\d+\\.\\s.*?)(?=(\\d+\\.\\s)|$)", Pattern.DOTALL);

    private static final Pattern NUMBERED_ITEM_PATTERN = Pattern.compile("\\d+\\.\\s");

    private static final int QA_DETECTION_THRESHOLD = 3;

    // Spring AI's TokenTextSplitter does not natively support overlap between chunks
    // (see spring-projects/spring-ai#2123, an open feature request as of this writing).
    // We apply a simple manual overlap here: the tail of each chunk is prepended to the
    // next one, so information near a chunk boundary isn't lost entirely from either side.
    private static final int OVERLAP_CHARS = 150;

    public boolean looksLikeQaFormat(String text) {
        Matcher matcher = NUMBERED_ITEM_PATTERN.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
            if (count >= QA_DETECTION_THRESHOLD) {
                log.debug("Detected Q&A format (found {}+ numbered items)", QA_DETECTION_THRESHOLD);
                return true;
            }
        }
        return false;
    }

    public List<Document> chunkByQaPairs(String fullText, String filename) {
        List<Document> result = new ArrayList<>();
        Matcher matcher = QA_PATTERN.matcher(fullText);

        while (matcher.find()) {
            String chunkText = matcher.group(1).trim();
            if (chunkText.isEmpty()) continue;

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("fileName", filename);
            result.add(new Document(chunkText, metadata));
        }

        if (result.isEmpty()) {
            log.warn("Q&A chunking produced no chunks for file: {}, falling back to whole-text chunk", filename);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("fileName", filename);
            result.add(new Document(fullText, metadata));
        }

        log.info("Q&A chunking produced {} chunks for file: {}", result.size(), filename);
        return result;
    }

    public List<Document> chunkByTokens(List<Document> rawDocuments) {
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.apply(rawDocuments);
        List<Document> withOverlap = applyOverlap(chunks);
        log.info("Token-based chunking produced {} chunks (with {}-char overlap applied)",
                withOverlap.size(), OVERLAP_CHARS);
        return withOverlap;
    }

    /**
     * Prepends the tail of each chunk to the start of the next chunk, so semantic
     * connections near a chunk boundary are preserved in at least one chunk.
     */
    private List<Document> applyOverlap(List<Document> chunks) {
        if (chunks.size() <= 1) {
            return chunks;
        }

        List<Document> result = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Document current = chunks.get(i);
            String text = current.getText();

            if (i > 0) {
                String previousText = chunks.get(i - 1).getText();
                String overlap = previousText.length() > OVERLAP_CHARS
                        ? previousText.substring(previousText.length() - OVERLAP_CHARS)
                        : previousText;
                text = overlap + " " + text;
            }

            result.add(new Document(text, current.getMetadata()));
        }

        return result;
    }
}