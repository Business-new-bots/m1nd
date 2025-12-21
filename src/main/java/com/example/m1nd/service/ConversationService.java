package com.example.m1nd.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ConversationService {
    
    // Храним историю диалогов для каждого пользователя
    private final Map<Long, List<Map<String, String>>> conversations = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY_SIZE = 10;
    
    public void addMessage(Long userId, String role, String content) {
        conversations.computeIfAbsent(userId, k -> new ArrayList<>())
            .add(Map.of("role", role, "content", content));
        
        // Ограничиваем историю последними сообщениями
        List<Map<String, String>> history = conversations.get(userId);
        if (history.size() > MAX_HISTORY_SIZE) {
            // Удаляем старые сообщения, оставляя системный промпт
            int toRemove = history.size() - MAX_HISTORY_SIZE;
            for (int i = 0; i < toRemove; i++) {
                if (history.size() > 1 && !history.get(0).get("role").equals("system")) {
                    history.remove(0);
                }
            }
        }
    }
    
    public List<Map<String, String>> getHistory(Long userId) {
        return conversations.getOrDefault(userId, new ArrayList<>());
    }
    
    public void clearHistory(Long userId) {
        conversations.remove(userId);
    }
    
    public void initializeHistory(Long userId, String systemPrompt) {
        List<Map<String, String>> history = conversations.computeIfAbsent(userId, k -> new ArrayList<>());
        if (history.isEmpty() || !history.get(0).get("role").equals("system")) {
            history.add(0, Map.of("role", "system", "content", systemPrompt));
        }
    }
}

