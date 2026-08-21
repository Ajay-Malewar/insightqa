package com.ajaymalewar.insightqa.controller;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final VectorStore vectorStore;

    public DocumentController(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        String lowerFilename = filename != null ? filename.toLowerCase(Locale.ROOT) : "";

        List<Document> documents;

        if (lowerFilename.endsWith(".pdf")) {
            documents = readPdf(file, filename);
        } else {
            documents = readText(file, filename);
        }

        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.apply(documents);

        vectorStore.add(chunks);

        return "Uploaded and indexed " + chunks.size() + " chunks from " + filename;
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

        return documents;
    }
}