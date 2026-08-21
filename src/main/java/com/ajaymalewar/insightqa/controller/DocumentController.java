package com.ajaymalewar.insightqa.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final VectorStore vectorStore;

    private static final Pattern QA_PATTERN = Pattern.compile(
            "(\\d+\\.\\s.*?)(?=(\\d+\\.\\s)|$)", Pattern.DOTALL);

    public DocumentController(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        String lowerFilename = filename != null ? filename.toLowerCase(Locale.ROOT) : "";
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        log.info("Upload started - user: {}, file: {}, size: {} bytes", username, filename, file.getSize());

        List<Document> rawDocuments;

        if (lowerFilename.endsWith(".pdf")) {
            rawDocuments = readPdf(file, filename);
        } else {
            rawDocuments = readText(file, filename);
        }

        String fullText = rawDocuments.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        boolean qaFormat = looksLikeQaFormat(fullText);
        List<Document> chunks = qaFormat
                ? chunkByQaPairs(fullText, filename)
                : chunkByTokens(rawDocuments);

        // Tag every chunk with the uploader's username so retrieval can be scoped per-user.
        chunks.forEach(doc -> doc.getMetadata().put("username", username));

        vectorStore.add(chunks);

        log.info("Upload completed - user: {}, file: {}, chunks indexed: {}, strategy: {}",
                username, filename, chunks.size(), qaFormat ? "qa-pairs" : "token-split");

        return "Uploaded and indexed " + chunks.size() + " chunks from " + filename;
    }

    private boolean looksLikeQaFormat(String text) {
        Matcher matcher = Pattern.compile("\\d+\\.\\s").matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
            if (count >= 3) return true;
        }
        return false;
    }

    private List<Document> chunkByQaPairs(String fullText, String filename) {
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
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("fileName", filename);
            result.add(new Document(fullText, metadata));
        }

        return result;
    }

    private List<Document> chunkByTokens(List<Document> rawDocuments) {
        TokenTextSplitter splitter = new TokenTextSplitter();
        return splitter.apply(rawDocuments);
    }

    private List<Document> readText(MultipartFile file, String filename) throws IOException {
        Resource resource = new InputStreamResource(file.getInputStream());
        TextReader textReader = new TextReader(resource);
        textReader.getCustomMetadata().put("fileName", filename);
        return textReader.get();
    }

    private List<Document> readPdf(MultipartFile file, String filename) throws IOException {
        Resource resource = new InputStreamResource(file.getInputStream());

        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                .withPageTopMargin(0)
                .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                        .withNumberOfTopTextLinesToDelete(0)
                        .build())
                .withPagesPerDocument(1)
                .build();

        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource, config);
        List<Document> documents = pdfReader.get();

        documents.forEach(doc -> doc.getMetadata().put("fileName", filename));

        log.info("PDF parsed - file: {}, pages: {}", filename, documents.size());

        return documents;
    }
}