package com.example.m1nd.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMService {
    
    private final WebClient.Builder webClientBuilder;
    private final ConversationService conversationService;
    private final PromptService promptService;
    private final SearchService searchService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${llm.api.provider}")
    private String provider;
    
    @Value("${llm.api.groq.api-key}")
    private String groqApiKey;
    
    @Value("${llm.api.groq.model}")
    private String groqModel;
    
    @Value("${llm.api.groq.url}")
    private String groqUrl;
    
    @Value("${llm.api.groq.temperature:0.7}")
    private Double groqTemperature;
    
    @Value("${llm.api.groq.max-tokens:500}")
    private Integer groqMaxTokens;
    
    @Value("${llm.api.huggingface.api-key:}")
    private String huggingfaceApiKey;
    
    @Value("${llm.api.huggingface.model:}")
    private String huggingfaceModel;
    
    @Value("${llm.api.huggingface.url:}")
    private String huggingfaceUrl;
    
    @Value("${llm.api.gemini.api-key:}")
    private String geminiApiKey;
    
    @Value("${llm.api.gemini.url:}")
    private String geminiUrl;
    
    @Value("${llm.api.openai.api-key:}")
    private String openaiApiKey;
    
    @Value("${llm.api.openai.model:}")
    private String openaiModel;
    
    @Value("${llm.api.openai.url:}")
    private String openaiUrl;
    
    public Mono<String> getAnswer(String question, Long userId) {
        // Получаем промпт из сервиса
        String systemPrompt = promptService.getPrompt();
        // Инициализируем историю диалога, если её нет
        conversationService.initializeHistory(userId, systemPrompt);
        
        // Проверяем, нужен ли поиск для этого вопроса
        boolean needsSearch = shouldSearch(question);
        
        if (needsSearch) {
            log.info("Выполняю поиск для вопроса: {}", question);
            // Выполняем поиск и добавляем результаты в контекст
            return searchService.search(question)
                .flatMap(searchResults -> {
                    String searchContext = searchService.formatSearchResults(searchResults);
                    String enhancedQuestion = question + searchContext;
                    
                    // Добавляем вопрос с результатами поиска в историю
                    conversationService.addMessage(userId, "user", enhancedQuestion);
                    
                    // Получаем историю диалога
                    List<Map<String, String>> history = conversationService.getHistory(userId);
                    
                    // Выбираем провайдера и отправляем запрос
                    return switch (provider.toLowerCase()) {
                        case "groq" -> getAnswerFromGroq(history, userId);
                        case "openai" -> getAnswerFromOpenAI(history, userId);
                        case "huggingface" -> getAnswerFromHuggingFace(enhancedQuestion, userId);
                        case "gemini" -> getAnswerFromGemini(enhancedQuestion, userId);
                        default -> Mono.error(new IllegalArgumentException("Неподдерживаемый провайдер: " + provider));
                    };
                });
        } else {
            // Добавляем вопрос пользователя в историю
            conversationService.addMessage(userId, "user", question);
            
            // Получаем историю диалога
            List<Map<String, String>> history = conversationService.getHistory(userId);
            
            // Выбираем провайдера и отправляем запрос
            return switch (provider.toLowerCase()) {
                case "groq" -> getAnswerFromGroq(history, userId);
                case "openai" -> getAnswerFromOpenAI(history, userId);
                case "huggingface" -> getAnswerFromHuggingFace(question, userId);
                case "gemini" -> getAnswerFromGemini(question, userId);
                default -> Mono.error(new IllegalArgumentException("Неподдерживаемый провайдер: " + provider));
            };
        }
    }
    
    /**
     * Определяет, нужен ли поиск для данного вопроса
     */
    private boolean shouldSearch(String question) {
        String lowerQuestion = question.toLowerCase();
        
        // Ключевые слова, которые указывают на необходимость поиска
        String[] searchKeywords = {
            "текст песни", "текст", "lyrics", "песня",
            "актуальн", "новост", "курс", "цена", "стоимость",
            "погода", "weather", "сегодня", "сейчас",
            "найди", "поиск", "ищи", "найти"
        };
        
        for (String keyword : searchKeywords) {
            if (lowerQuestion.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }
    
    private Mono<String> getAnswerFromGroq(List<Map<String, String>> history, Long userId) {
        WebClient webClient = webClientBuilder.build();
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", groqModel);
        requestBody.put("messages", convertHistoryToMessages(history));
        requestBody.put("temperature", groqTemperature);
        requestBody.put("max_tokens", groqMaxTokens);
        
        return webClient.post()
            .uri(groqUrl)
            .header("Authorization", "Bearer " + groqApiKey)
            .header("Content-Type", "application/json")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(String.class)
            .map(responseBody -> {
                try {
                    JsonNode response = objectMapper.readTree(responseBody);
                    String answer = response.get("choices").get(0).get("message").get("content").asText();
                    conversationService.addMessage(userId, "assistant", answer);
                    return answer;
                } catch (Exception e) {
                    log.error("Ошибка при парсинге ответа от Groq API", e);
                    throw new RuntimeException("Ошибка при обработке ответа", e);
                }
            })
            .doOnError(error -> log.error("Ошибка при запросе к Groq API", error));
    }
    
    private Mono<String> getAnswerFromOpenAI(List<Map<String, String>> history, Long userId) {
        WebClient webClient = webClientBuilder.build();
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", openaiModel);
        requestBody.put("messages", convertHistoryToMessages(history));
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 500);
        
        return webClient.post()
            .uri(openaiUrl)
            .header("Authorization", "Bearer " + openaiApiKey)
            .header("Content-Type", "application/json")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(String.class)
            .map(responseBody -> {
                try {
                    JsonNode response = objectMapper.readTree(responseBody);
                    String answer = response.get("choices").get(0).get("message").get("content").asText();
                    conversationService.addMessage(userId, "assistant", answer);
                    return answer;
                } catch (Exception e) {
                    log.error("Ошибка при парсинге ответа от OpenAI API", e);
                    throw new RuntimeException("Ошибка при обработке ответа", e);
                }
            })
            .doOnError(error -> log.error("Ошибка при запросе к OpenAI API", error));
    }
    
    private Mono<String> getAnswerFromHuggingFace(String question, Long userId) {
        WebClient webClient = webClientBuilder.build();
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("inputs", question);
        
        String url = huggingfaceUrl + "/" + huggingfaceModel;
        
        return webClient.post()
            .uri(url)
            .header("Authorization", "Bearer " + huggingfaceApiKey)
            .header("Content-Type", "application/json")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(String.class)
            .map(responseBody -> {
                try {
                    JsonNode response = objectMapper.readTree(responseBody);
                    String answer;
                    if (response.isArray() && response.size() > 0) {
                        answer = response.get(0).get("generated_text").asText();
                    } else {
                        answer = response.get("generated_text").asText();
                    }
                    conversationService.addMessage(userId, "assistant", answer);
                    return answer;
                } catch (Exception e) {
                    log.error("Ошибка при парсинге ответа от HuggingFace API", e);
                    throw new RuntimeException("Ошибка при обработке ответа", e);
                }
            })
            .doOnError(error -> log.error("Ошибка при запросе к HuggingFace API", error));
    }
    
    private Mono<String> getAnswerFromGemini(String question, Long userId) {
        WebClient webClient = webClientBuilder.build();
        
        Map<String, Object> contents = new HashMap<>();
        Map<String, Object> part = new HashMap<>();
        part.put("text", question);
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(part);
        contents.put("parts", parts);
        
        List<Map<String, Object>> contentsList = new ArrayList<>();
        contentsList.add(contents);
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", contentsList);
        
        return webClient.post()
            .uri(geminiUrl + "?key=" + geminiApiKey)
            .header("Content-Type", "application/json")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(String.class)
            .map(responseBody -> {
                try {
                    JsonNode response = objectMapper.readTree(responseBody);
                    String answer = response.get("candidates").get(0).get("content").get("parts").get(0).get("text").asText();
                    conversationService.addMessage(userId, "assistant", answer);
                    return answer;
                } catch (Exception e) {
                    log.error("Ошибка при парсинге ответа от Gemini API", e);
                    throw new RuntimeException("Ошибка при обработке ответа", e);
                }
            })
            .doOnError(error -> log.error("Ошибка при запросе к Gemini API", error));
    }
    
    private List<Map<String, String>> convertHistoryToMessages(List<Map<String, String>> history) {
        return new ArrayList<>(history);
    }
}

