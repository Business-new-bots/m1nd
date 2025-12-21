package com.example.m1nd.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class PromptService {
    
    @Value("${app.data.prompt-file:prompt.txt}")
    private String promptFile;
    
    private String cachedPrompt;
    
    @PostConstruct
    public void loadPrompt() {
        try {
            InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream(promptFile);
            
            if (inputStream == null) {
                log.warn("Файл промпта {} не найден, используем дефолтный", promptFile);
                cachedPrompt = "Ты полезный ассистент. Отвечай кратко и по делу.";
                return;
            }
            
            cachedPrompt = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            log.info("Промпт загружен из файла {} ({} символов)", promptFile, cachedPrompt.length());
        } catch (Exception e) {
            log.error("Ошибка при загрузке промпта из файла {}", promptFile, e);
            cachedPrompt = "Ты полезный ассистент. Отвечай кратко и по делу.";
        }
    }
    
    public String getPrompt() {
        return cachedPrompt;
    }
    
    // Метод для перезагрузки промпта (если нужно обновлять без перезапуска)
    public void reloadPrompt() {
        loadPrompt();
    }
}

