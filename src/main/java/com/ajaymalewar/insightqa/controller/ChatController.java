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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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

        log.info("RAG question - conversationId: {}, question: \"{}\"", conversationId, question);

        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(3)
                .build();

        List<Document> relevantChunks = vectorStore.similaritySearch(searchRequest);
        log.info("Retrieved {} relevant chunks for conversationId: {}", relevantChunks.size(), conversationId);

        String context = relevantChunks.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        List<String> history = conversationStore.getHistory(conversationId);
        String historyText = history.isEmpty() ? "(none)" : String.join("\n", history);

        String promptText = """
                Answer the question using ONLY the context below.
                If the answer is not in the context, say "I don't have enough information to answer that."
                Use the previous conversation only to understand follow-up questions (e.g. "what about X").

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

        log.info("RAG answer generated for conversationId: {}, sources used: {}", conversationId, relevantChunks.size());

        List<QaResponse.SourceChunk> sources = relevantChunks.stream()
                .map(doc -> new QaResponse.SourceChunk(
                        String.valueOf(doc.getMetadata().getOrDefault("fileName", "unknown")),
                        snippet(doc.getText())
                ))
                .collect(Collectors.toList());

        return new QaResponse(answer, sources);
    }

    private String snippet(String text) {
        if (text == null) return "";
        return text.length() <= 150 ? text : text.substring(0, 150) + "...";
    }
}