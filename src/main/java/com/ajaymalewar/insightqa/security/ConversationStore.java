package com.ajaymalewar.insightqa.security;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ConversationStore {

    private static final int MAX_TURNS = 5;

    private final Map<String, List<String>> conversations = new ConcurrentHashMap<>();

    public List<String> getHistory(String conversationId) {
        return conversations.getOrDefault(conversationId, List.of());
    }

    public void addTurn(String conversationId, String question, String answer) {
        List<String> history = conversations.computeIfAbsent(conversationId, id -> new CopyOnWriteArrayList<>());
        history.add("User: " + question);
        history.add("Assistant: " + answer);

        while (history.size() > MAX_TURNS * 2) {
            history.remove(0);
        }
    }
}