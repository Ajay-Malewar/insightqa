package com.ajaymalewar.insightqa.controller;

import com.ajaymalewar.insightqa.dto.QaResponse;
import com.ajaymalewar.insightqa.security.ConversationStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ConversationStore conversationStore;

    public ChatController(@Qualifier("openAiChatModel") ChatModel chatModel,
                           VectorStore vectorStore,
                           ConversationStore conversationStore) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.vectorStore = vectorStore;
        this.conversationStore = conversationStore;
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String question) {
        log.info("Plain chat request - question: \"{}\"", question);
        String answer = chatClient.prompt()
                .user(question)
                .call()
                .content();
        log.info("Plain chat response generated, length: {} chars", answer.length());
        return answer;
    }

    @GetMapping("/qa")
    public QaResponse qa(@RequestParam String question,
                          @RequestParam(defaultValue = "default") String conversationId) {

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("RAG question - user: {}, conversationId: {}, question: \"{}\"", username, conversationId, question);

        String safeUsername = username.replace("'", "");

        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(3)
                .filterExpression("username == '" + safeUsername + "'")
                .build();

        List<Document> relevantChunks = vectorStore.similaritySearch(searchRequest);
        log.info("Retrieved {} relevant chunks for user: {}, conversationId: {}", relevantChunks.size(), username, conversationId);

        String context = relevantChunks.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        List<String> history = conversationStore.getHistory(conversationId);
        String historyText = history.isEmpty() ? "(none)" : String.join("\n", history);

        String promptText = """
                Answer the question using ONLY the context below, in one or two complete, natural sentences.
                If the answer is not in the context, say "I don't have enough information to answer that."
                Use the previous conversation only to understand follow-up questions (e.g. "what about X").
                Respond in plain text only, with no markdown formatting such as asterisks, bullet points, or headers.

                Previous conversation:
                %s

                Context:
                %s

                Question: %s
                """.formatted(historyText, context, question);

        String answer = chatClient.prompt()
                .user(promptText)
                .call()
                .content();

        conversationStore.addTurn(conversationId, question, answer);

        log.info("RAG answer generated for user: {}, conversationId: {}, sources used: {}", username, conversationId, relevantChunks.size());

        Map<String, QaResponse.SourceChunk> uniqueSources = new LinkedHashMap<>();
        for (Document doc : relevantChunks) {
            String text = doc.getText();
            String normalized = normalize(text);
            uniqueSources.putIfAbsent(normalized, new QaResponse.SourceChunk(
                    String.valueOf(doc.getMetadata().getOrDefault("fileName", "unknown")),
                    snippet(text)
            ));
        }

        return new QaResponse(answer, List.copyOf(uniqueSources.values()));
    }

    private String normalize(String text) {
        if (text == null) return "";
        return text.replaceAll("[^\\p{L}\\p{Nd}\\s]", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
    }

    private String snippet(String text) {
        if (text == null) return "";
        return text.length() <= 150 ? text : text.substring(0, 150) + "...";
    }
}