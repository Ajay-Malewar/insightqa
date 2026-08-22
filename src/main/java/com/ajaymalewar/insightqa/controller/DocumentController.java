package com.ajaymalewar.insightqa.controller;

import com.ajaymalewar.insightqa.dto.DocumentSummary;
import com.ajaymalewar.insightqa.service.DocumentChunkingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final VectorStore vectorStore;
    private final DocumentChunkingStrategy chunkingStrategy;
    private final JdbcTemplate jdbcTemplate;

    public DocumentController(VectorStore vectorStore, DocumentChunkingStrategy chunkingStrategy, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.chunkingStrategy = chunkingStrategy;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        String lowerFilename = filename != null ? filename.toLowerCase(Locale.ROOT) : "";
        String username = currentUsername();

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

        boolean qaFormat = chunkingStrategy.looksLikeQaFormat(fullText);
        List<Document> chunks = qaFormat
                ? chunkingStrategy.chunkByQaPairs(fullText, filename)
                : chunkingStrategy.chunkByTokens(rawDocuments);

        chunks.forEach(doc -> doc.getMetadata().put("username", username));

        vectorStore.add(chunks);

        log.info("Upload completed - user: {}, file: {}, chunks indexed: {}, strategy: {}",
                username, filename, chunks.size(), qaFormat ? "qa-pairs" : "token-split");

        return "Uploaded and indexed " + chunks.size() + " chunks from " + filename;
    }

    @GetMapping
    public List<DocumentSummary> listDocuments() {
        String username = currentUsername();
        log.info("Listing documents for user: {}", username);

        String sql = """
                SELECT metadata->>'fileName' AS file_name, COUNT(*) AS chunk_count
                FROM vector_store
                WHERE metadata->>'username' = ?
                GROUP BY metadata->>'fileName'
                ORDER BY file_name
                """;

        List<DocumentSummary> results = jdbcTemplate.query(sql,
                (rs, rowNum) -> new DocumentSummary(rs.getString("file_name"), rs.getLong("chunk_count")),
                username);

        log.info("Found {} distinct documents for user: {}", results.size(), username);
        return results;
    }

    @DeleteMapping("/{fileName}")
    public String deleteDocument(@PathVariable String fileName) {
        String username = currentUsername();
        String safeUsername = username.replace("'", "");
        String safeFileName = fileName.replace("'", "");

        log.info("Delete requested - user: {}, file: {}", username, fileName);

        vectorStore.delete("username == '" + safeUsername + "' AND fileName == '" + safeFileName + "'");

        log.info("Delete completed - user: {}, file: {}", username, fileName);
        return "Deleted document: " + fileName;
    }

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
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