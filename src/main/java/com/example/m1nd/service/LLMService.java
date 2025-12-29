package com.example.m1nd.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMService {
    
    private final WebClient.Builder webClientBuilder;
    private final ConversationService conversationService;
    private final PromptService promptService;
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
            .timeout(Duration.ofSeconds(30))
            .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                .filter(throwable -> {
                    // Retry только для временных ошибок
                    if (throwable instanceof WebClientResponseException) {
                        WebClientResponseException ex = (WebClientResponseException) throwable;
                        int status = ex.getStatusCode().value();
                        // Retry для 429 (rate limit) и 5xx ошибок
                        return status == 429 || (status >= 500 && status < 600);
                    }
                    return throwable instanceof TimeoutException;
                })
                .doBeforeRetry(retrySignal -> 
                    log.warn("Повторная попытка запроса к Groq API (попытка {})", 
                        retrySignal.totalRetries() + 1))
            )
            .map(responseBody -> {
                try {
                    JsonNode response = objectMapper.readTree(responseBody);
                    JsonNode choices = response.get("choices");
                    if (choices == null || !choices.isArray() || choices.size() == 0) {
                        log.error("Пустой ответ от Groq API: {}", responseBody);
                        throw new RuntimeException("Пустой ответ от API");
                    }
                    JsonNode message = choices.get(0).get("message");
                    if (message == null || message.get("content") == null) {
                        log.error("Некорректная структура ответа от Groq API: {}", responseBody);
                        throw new RuntimeException("Некорректная структура ответа");
                    }
                    String answer = message.get("content").asText();
                    if (answer == null || answer.isEmpty()) {
                        log.error("Пустое содержимое ответа от Groq API");
                        throw new RuntimeException("Пустое содержимое ответа");
                    }
                    conversationService.addMessage(userId, "assistant", answer);
                    return answer;
                } catch (JsonProcessingException e) {
                    log.error("Ошибка при парсинге ответа от Groq API. Ответ: {}", responseBody, e);
                    throw new RuntimeException("Ошибка при обработке ответа", e);
                }
            })
            .doOnError(error -> {
                if (error instanceof WebClientResponseException) {
                    WebClientResponseException ex = (WebClientResponseException) error;
                    log.error("Ошибка HTTP при запросе к Groq API. Status: {}, Body: {}", 
                        ex.getStatusCode(), ex.getResponseBodyAsString());
                } else if (error instanceof TimeoutException) {
                    log.error("Таймаут при запросе к Groq API (превышено 30 секунд)");
                } else {
                    log.error("Ошибка при запросе к Groq API", error);
                }
            });
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

