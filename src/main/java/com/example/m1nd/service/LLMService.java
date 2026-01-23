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
    private final com.example.m1nd.service.tools.ToolService toolService;
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
    
    @Value("${llm.api.deepseek.api-key:}")
    private String deepseekApiKey;
    
    @Value("${llm.api.deepseek.model:deepseek-chat}")
    private String deepseekModel;
    
    @Value("${llm.api.deepseek.url:https://api.deepseek.com/v1/chat/completions}")
    private String deepseekUrl;
    
    @Value("${llm.api.deepseek.temperature:0.7}")
    private Double deepseekTemperature;
    
    @Value("${llm.api.deepseek.max-tokens:2000}")
    private Integer deepseekMaxTokens;
    
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
    
    @Value("${llm.api.yandexgpt.api-key:}")
    private String yandexgptApiKey;
    
    @Value("${llm.api.yandexgpt.folder-id:}")
    private String yandexgptFolderId;
    
    @Value("${llm.api.yandexgpt.agent-id:}")
    private String yandexgptAgentId;
    
    @Value("${llm.api.yandexgpt.project:}")
    private String yandexgptProject;
    
    @Value("${llm.api.yandexgpt.url:https://llm.api.cloud.yandex.net/foundationModels/v1/completion}")
    private String yandexgptUrl;
    
    @Value("${llm.api.yandexgpt.model:yandexgpt}")
    private String yandexgptModel;
    
    @Value("${llm.api.yandexgpt.temperature:0.7}")
    private Double yandexgptTemperature;
    
    @Value("${llm.api.yandexgpt.max-tokens:2000}")
    private Integer yandexgptMaxTokens;
    
    public Mono<String> getAnswer(String question, Long userId) {
        // Промпт теперь в агенте, но инициализируем историю для совместимости
        conversationService.initializeHistory(userId, "");
        
        // Добавляем вопрос пользователя в историю
        conversationService.addMessage(userId, "user", question);
        
        // Получаем историю диалога
        List<Map<String, String>> history = conversationService.getHistory(userId);
        
        // ВРЕМЕННО: Используем только YandexGPT Agent (остальные провайдеры закомментированы)
        log.info("Начинаем запрос к LLM для пользователя {}. Провайдер: YandexGPT Agent (временно единственный)", userId);
        return sendRequestToYandexGPT(history, userId);
        
        /* ВРЕМЕННО ЗАКОММЕНТИРОВАНО - вернуть при необходимости
        // Сначала пробуем DeepSeek, если ошибка - fallback на Groq
        log.info("Начинаем запрос к LLM для пользователя {}. Провайдер: DeepSeek (primary)", userId);
        return getAnswerWithFunctionCalling(history, userId, "deepseek")
            .onErrorResume(error -> {
                log.warn("DeepSeek не ответил, пробуем Groq как fallback. Ошибка: {}", error.getMessage());
                log.info("Переключаемся на провайдер: Groq (fallback) для пользователя {}", userId);
                return getAnswerWithFunctionCalling(history, userId, "groq")
                    .onErrorResume(groqError -> {
                        log.error("И DeepSeek, и Groq не ответили. Последняя ошибка: {}", groqError.getMessage());
                        // Если оба провайдера не работают, возвращаем ошибку
                        return Mono.error(new RuntimeException("Не удалось получить ответ ни от DeepSeek, ни от Groq", groqError));
                    });
            });
        */
    }
    
    /**
     * Получает ответ с поддержкой Function Calling (цикл обработки tool_calls)
     */
    private Mono<String> getAnswerWithFunctionCalling(List<Map<String, String>> history, Long userId, String providerType) {
        return getAnswerWithTools(history, userId, providerType, 0, 5); // максимум 5 итераций
    }
    
    /**
     * Рекурсивный метод для обработки function calling с циклом
     */
    private Mono<String> getAnswerWithTools(List<Map<String, String>> history, Long userId, String providerType, int iteration, int maxIterations) {
        if (iteration >= maxIterations) {
            log.warn("Достигнут максимум итераций function calling");
            return Mono.just("Извините, не удалось получить ответ после нескольких попыток использования инструментов.");
        }
        
        // Получаем список инструментов для LLM
        List<Map<String, Object>> tools = toolService.getToolsForLLM();
        
        // Отправляем запрос с инструментами
        Mono<String> responseMono = switch (providerType) {
            case "groq" -> sendRequestWithTools(history, tools, userId, providerType);
            case "deepseek" -> sendRequestWithTools(history, tools, userId, providerType);
            case "openai" -> sendRequestWithTools(history, tools, userId, providerType);
            default -> Mono.error(new IllegalArgumentException("Неподдерживаемый провайдер: " + providerType));
        };
        
        return responseMono.flatMap(response -> {
            try {
                log.debug("Получен ответ от {} API (длина: {} символов)", providerType, response.length());
                JsonNode responseJson = objectMapper.readTree(response);
                JsonNode choices = responseJson.get("choices");
                if (choices == null || !choices.isArray() || choices.size() == 0) {
                    log.warn("Пустой ответ от {} API", providerType);
                    return Mono.just("Ошибка: пустой ответ от API");
                }
                
                JsonNode message = choices.get(0).get("message");
                if (message == null) {
                    log.warn("Некорректная структура ответа от {} API", providerType);
                    return Mono.just("Ошибка: некорректная структура ответа");
                }
                
                // Проверяем, есть ли tool_calls
                JsonNode toolCalls = message.get("tool_calls");
                if (toolCalls != null && toolCalls.isArray() && toolCalls.size() > 0) {
                    log.info("Обнаружены tool_calls, выполняю инструменты...");
                    
                    // Добавляем сообщение ассистента с tool_calls в историю
                    // Для Groq/OpenAI нужно передавать сообщение с tool_calls в формате JSON
                    Map<String, Object> assistantMessage = new HashMap<>();
                    assistantMessage.put("role", "assistant");
                    if (message.has("content") && !message.get("content").isNull()) {
                        assistantMessage.put("content", message.get("content").asText());
                    }
                    assistantMessage.put("tool_calls", toolCalls);
                    
                    // Добавляем в историю как Map<String, Object> для правильной сериализации
                    Map<String, String> assistantMsg = new HashMap<>();
                    assistantMsg.put("role", "assistant");
                    if (message.has("content") && !message.get("content").isNull()) {
                        assistantMsg.put("content", message.get("content").asText());
                    }
                    // tool_calls будет добавлен при конвертации для API
                    history.add(assistantMsg);
                    
                    // Выполняем все инструменты
                    for (JsonNode toolCall : toolCalls) {
                        String toolName = toolCall.get("function").get("name").asText();
                        String toolCallId = toolCall.get("id").asText();
                        JsonNode arguments = toolCall.get("function").get("arguments");
                        
                        @SuppressWarnings("unchecked")
                        Map<String, Object> params = (Map<String, Object>) objectMapper.readValue(arguments.asText(), Map.class);
                        String result = toolService.executeTool(toolName, params);
                        
                        // Добавляем результат инструмента в историю
                        Map<String, String> toolMessage = new HashMap<>();
                        toolMessage.put("role", "tool");
                        toolMessage.put("tool_call_id", toolCallId);
                        toolMessage.put("name", toolName);
                        toolMessage.put("content", result);
                        history.add(toolMessage);
                    }
                    
                    // Повторяем запрос с результатами инструментов
                    return getAnswerWithTools(history, userId, providerType, iteration + 1, maxIterations);
                } else {
                    // Нет tool_calls, возвращаем финальный ответ
                    String answer = message.get("content").asText();
                    if (answer == null || answer.isEmpty()) {
                        log.warn("Пустое содержимое ответа от {} API", providerType);
                        return Mono.just("Извините, не удалось получить ответ.");
                    }
                    log.info("Успешно получен ответ от {} API. Длина ответа: {} символов", providerType, answer.length());
                    conversationService.addMessage(userId, "assistant", answer);
                    return Mono.just(answer);
                }
            } catch (Exception e) {
                log.error("Ошибка при обработке ответа с function calling", e);
                return Mono.just("Ошибка при обработке ответа: " + e.getMessage());
            }
        });
    }
    
    /**
     * Отправляет запрос к API с инструментами
     */
    private Mono<String> sendRequestWithTools(List<Map<String, String>> history, List<Map<String, Object>> tools, Long userId, String providerType) {
        WebClient webClient = webClientBuilder.build();
        Map<String, Object> requestBody = new HashMap<>();
        
        if ("groq".equals(providerType)) {
            log.info("Отправляем запрос к Groq API. URL: {}, Модель: {}", groqUrl, groqModel);
            requestBody.put("model", groqModel);
            requestBody.put("messages", convertHistoryToMessages(history));
            requestBody.put("temperature", groqTemperature);
            requestBody.put("max_tokens", groqMaxTokens);
            if (!tools.isEmpty()) {
                requestBody.put("tools", tools);
                requestBody.put("tool_choice", "auto");
                log.debug("Запрос к Groq включает {} инструментов", tools.size());
            }
            
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
                        if (throwable instanceof WebClientResponseException) {
                            WebClientResponseException ex = (WebClientResponseException) throwable;
                            int status = ex.getStatusCode().value();
                            return status == 429 || (status >= 500 && status < 600);
                        }
                        return throwable instanceof TimeoutException;
                    })
                );
        } else if ("deepseek".equals(providerType)) {
            log.info("Отправляем запрос к DeepSeek API. URL: {}, Модель: {}", deepseekUrl, deepseekModel);
            requestBody.put("model", deepseekModel);
            requestBody.put("messages", convertHistoryToMessages(history));
            requestBody.put("temperature", deepseekTemperature);
            requestBody.put("max_tokens", deepseekMaxTokens);
            if (!tools.isEmpty()) {
                requestBody.put("tools", tools);
                requestBody.put("tool_choice", "auto");
                log.debug("Запрос к DeepSeek включает {} инструментов", tools.size());
            }
            
            return webClient.post()
                .uri(deepseekUrl)
                .header("Authorization", "Bearer " + deepseekApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                    .filter(throwable -> {
                        if (throwable instanceof WebClientResponseException) {
                            WebClientResponseException ex = (WebClientResponseException) throwable;
                            int status = ex.getStatusCode().value();
                            return status == 429 || (status >= 500 && status < 600);
                        }
                        return throwable instanceof TimeoutException;
                    })
                    .doBeforeRetry(retrySignal -> 
                        log.warn("Повторная попытка запроса к DeepSeek API (попытка {})", 
                            retrySignal.totalRetries() + 1))
                )
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException) {
                        WebClientResponseException ex = (WebClientResponseException) error;
                        log.error("Ошибка HTTP при запросе к DeepSeek API. Status: {}, Body: {}", 
                            ex.getStatusCode(), ex.getResponseBodyAsString());
                    } else if (error instanceof TimeoutException) {
                        log.error("Таймаут при запросе к DeepSeek API (превышено 30 секунд)");
                    } else {
                        log.error("Ошибка при запросе к DeepSeek API", error);
                    }
                });
        } else if ("openai".equals(providerType)) {
            log.info("Отправляем запрос к OpenAI API (ChatGPT). URL: {}, Модель: {}", openaiUrl, openaiModel);
            requestBody.put("model", openaiModel);
            requestBody.put("messages", convertHistoryToMessages(history));
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 2000);
            if (!tools.isEmpty()) {
                requestBody.put("tools", tools);
                requestBody.put("tool_choice", "auto");
                log.debug("Запрос к OpenAI включает {} инструментов", tools.size());
            }
            
            return webClient.post()
                .uri(openaiUrl)
                .header("Authorization", "Bearer " + openaiApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30));
        }
        
        return Mono.error(new IllegalArgumentException("Неподдерживаемый провайдер: " + providerType));
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
    
    /**
     * Конвертирует историю диалога в формат для API
     * Поддерживает tool_calls и результаты инструментов
     */
    private List<Map<String, Object>> convertHistoryToMessages(List<Map<String, String>> history) {
        List<Map<String, Object>> messages = new ArrayList<>();
        
        for (Map<String, String> msg : history) {
            Map<String, Object> message = new HashMap<>(msg);
            
            // Если это сообщение tool, добавляем tool_call_id
            if ("tool".equals(msg.get("role")) && msg.containsKey("tool_call_id")) {
                message.put("tool_call_id", msg.get("tool_call_id"));
            }
            
            messages.add(message);
        }
        
        return messages;
    }
    
    /**
     * Отправляет запрос к YandexGPT через Responses API используя агента по ID
     * Формат соответствует Python SDK: client.responses.create(prompt={"id": "..."}, input="...")
     */
    private Mono<String> sendRequestToYandexGPT(List<Map<String, String>> history, Long userId) {
        WebClient webClient = webClientBuilder.build();
        
        // Проверяем наличие обязательных параметров
        if (yandexgptApiKey == null || yandexgptApiKey.isEmpty()) {
            log.error("YandexGPT API ключ не настроен");
            return Mono.error(new RuntimeException("YandexGPT API ключ не настроен. Установите переменную окружения YANDEXGPT_API_KEY"));
        }
        if (yandexgptAgentId == null || yandexgptAgentId.isEmpty()) {
            log.error("YandexGPT Agent ID не настроен");
            return Mono.error(new RuntimeException("YandexGPT Agent ID не настроен. Установите переменную окружения YANDEXGPT_AGENT_ID"));
        }
        
        // Логирование для отладки
        String apiKeyPreview = yandexgptApiKey != null && yandexgptApiKey.length() > 8 
            ? yandexgptApiKey.substring(0, 4) + "..." + yandexgptApiKey.substring(yandexgptApiKey.length() - 4)
            : "null";
        log.info("Используется API-ключ: {}", apiKeyPreview);
        log.info("Agent ID: {}", yandexgptAgentId);
        log.info("Project/Folder ID: {}", yandexgptFolderId);
        
        // Получаем последнее сообщение пользователя из истории
        String lastUserMessage = "";
        for (int i = history.size() - 1; i >= 0; i--) {
            Map<String, String> msg = history.get(i);
            if ("user".equals(msg.get("role"))) {
                lastUserMessage = msg.get("content");
                break;
            }
        }
        
        if (lastUserMessage.isEmpty()) {
            log.error("Не найдено сообщение пользователя в истории");
            return Mono.error(new RuntimeException("Не найдено сообщение пользователя"));
        }
        
        // Формируем тело запроса в формате Responses API с использованием агента по ID
        Map<String, Object> requestBody = new HashMap<>();
        
        // Используем prompt с id агента (как в Python примере: prompt={"id": "..."})
        Map<String, Object> prompt = new HashMap<>();
        prompt.put("id", yandexgptAgentId);
        requestBody.put("prompt", prompt);
        
        // Input - просто строка (как в Python примере: input="some message")
        requestBody.put("input", lastUserMessage);
        
        // Для поддержки контекста используем previous_response_id
        String previousResponseId = conversationService.getLastResponseId(userId);
        if (previousResponseId != null && !previousResponseId.isEmpty()) {
            requestBody.put("previous_response_id", previousResponseId);
            log.debug("Используется previous_response_id для контекста: {}", previousResponseId);
        } else {
            log.debug("Первый запрос в диалоге, previous_response_id не используется");
        }
        
        // Параметры агента (температура, max_tokens, инструкция) уже настроены в AI Studio
        // Их можно переопределить, но обычно не нужно, так как они берутся из настроек агента
        
        log.info("Отправляем запрос к YandexGPT Responses API. URL: {}, Agent ID: {}", yandexgptUrl, yandexgptAgentId);
        log.debug("Входное сообщение: {}", lastUserMessage);
        
        // Логируем тело запроса для отладки
        try {
            String requestBodyJson = objectMapper.writeValueAsString(requestBody);
            log.info("Тело запроса к YandexGPT Responses API: {}", requestBodyJson);
        } catch (Exception e) {
            log.warn("Не удалось залогировать тело запроса", e);
        }
        
        return webClient.post()
            .uri(yandexgptUrl)
            .header("Authorization", "Api-Key " + yandexgptApiKey)
            .header("Content-Type", "application/json")
            .header("OpenAI-Project", yandexgptFolderId)  // Folder ID в заголовке для Responses API
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(60))  // Увеличиваем таймаут для агента (может использовать WebSearch)
            .doOnNext(responseBody -> {
                // Логируем полный ответ перед парсингом
                log.info("Полный ответ от YandexGPT Responses API (длина: {} символов): {}", 
                    responseBody != null ? responseBody.length() : 0, responseBody);
            })
            .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                .filter(throwable -> {
                    if (throwable instanceof WebClientResponseException) {
                        WebClientResponseException ex = (WebClientResponseException) throwable;
                        int status = ex.getStatusCode().value();
                        return status == 429 || (status >= 500 && status < 600);
                    }
                    return throwable instanceof TimeoutException;
                })
                .doBeforeRetry(retrySignal -> 
                    log.warn("Повторная попытка запроса к YandexGPT Responses API (попытка {})", 
                        retrySignal.totalRetries() + 1))
            )
            .flatMap(response -> {
                try {
                    return parseYandexGPTResponsesAPIResponse(response, userId);
                } catch (Exception e) {
                    log.error("Ошибка при обработке ответа от YandexGPT Responses API. Ответ: {}", response, e);
                    return Mono.just("Ошибка при обработке ответа: " + e.getMessage());
                }
            })
            .doOnError(error -> {
                if (error instanceof WebClientResponseException) {
                    WebClientResponseException ex = (WebClientResponseException) error;
                    String errorBody = ex.getResponseBodyAsString();
                    log.error("Ошибка HTTP при запросе к YandexGPT Responses API. Status: {}, Body: {}", 
                        ex.getStatusCode(), errorBody != null ? errorBody : "пусто");
                } else if (error instanceof TimeoutException) {
                    log.error("Таймаут при запросе к YandexGPT Responses API (превышено 60 секунд)");
                } else {
                    log.error("Ошибка при запросе к YandexGPT Responses API", error);
                }
            });
    }
    
    /**
     * Конвертирует историю диалога в формат YandexGPT
     * YandexGPT использует формат: { "role": "...", "text": "..." }
     * Убираем сообщения с role="tool", так как YandexGPT их не поддерживает
     */
    private List<Map<String, Object>> convertHistoryToYandexGPTFormat(List<Map<String, String>> history) {
        List<Map<String, Object>> messages = new ArrayList<>();
        
        for (Map<String, String> msg : history) {
            String role = msg.get("role");
            String content = msg.get("content");
            
            // YandexGPT не поддерживает role "tool", пропускаем такие сообщения
            if ("tool".equals(role)) {
                log.debug("Пропускаем tool сообщение для YandexGPT (не поддерживается)");
                continue;
            }
            
            // Маппинг ролей: system/user/assistant остаются как есть
            // В YandexGPT используется "text" вместо "content"
            Map<String, Object> message = new HashMap<>();
            message.put("role", role);
            message.put("text", content != null ? content : "");
            messages.add(message);
        }
        
        return messages;
    }
    
    /**
     * Парсит ответ от YandexGPT API
     * Формат ответа: { "result": { "alternatives": [{ "message": { "role": "assistant", "text": "..." } }] } }
     */
    private Mono<String> parseYandexGPTResponse(String responseBody, Long userId) {
        try {
            log.debug("Получен ответ от YandexGPT API (длина: {} символов)", responseBody.length());
            JsonNode responseJson = objectMapper.readTree(responseBody);
            
            // Проверяем наличие ошибки
            if (responseJson.has("error")) {
                JsonNode error = responseJson.get("error");
                String errorMessage = error.has("message") ? error.get("message").asText() : "Неизвестная ошибка";
                String errorCode = error.has("code") ? error.get("code").asText() : "UNKNOWN";
                log.error("YandexGPT API вернул ошибку. Code: {}, Message: {}", errorCode, errorMessage);
                return Mono.error(new RuntimeException("YandexGPT API Error [" + errorCode + "]: " + errorMessage));
            }
            
            JsonNode result = responseJson.get("result");
            if (result == null) {
                log.warn("Пустой ответ от YandexGPT API. Полный ответ: {}", responseBody);
                return Mono.just("Ошибка: пустой ответ от API");
            }
            
            JsonNode alternatives = result.get("alternatives");
            if (alternatives == null || !alternatives.isArray() || alternatives.size() == 0) {
                log.warn("Нет альтернатив в ответе от YandexGPT API. Полный ответ: {}", responseBody);
                return Mono.just("Ошибка: некорректная структура ответа");
            }
            
            JsonNode firstAlternative = alternatives.get(0);
            JsonNode message = firstAlternative.get("message");
            if (message == null) {
                log.warn("Нет сообщения в ответе от YandexGPT API. Полный ответ: {}", responseBody);
                return Mono.just("Ошибка: некорректная структура ответа");
            }
            
            JsonNode textNode = message.get("text");
            if (textNode == null || textNode.isNull()) {
                log.warn("Пустое содержимое ответа от YandexGPT API");
                return Mono.just("Извините, не удалось получить ответ.");
            }
            
            String answer = textNode.asText();
            if (answer == null || answer.isEmpty()) {
                log.warn("Пустое содержимое ответа от YandexGPT API");
                return Mono.just("Извините, не удалось получить ответ.");
            }
            
            log.info("Успешно получен ответ от YandexGPT API. Длина ответа: {} символов", answer.length());
            conversationService.addMessage(userId, "assistant", answer);
            return Mono.just(answer);
        } catch (JsonProcessingException e) {
            log.error("Ошибка при парсинге JSON ответа от YandexGPT API. Ответ: {}", responseBody, e);
            return Mono.just("Ошибка при обработке ответа от API: " + e.getMessage());
        } catch (Exception e) {
            log.error("Неожиданная ошибка при парсинге ответа от YandexGPT API", e);
            return Mono.just("Ошибка при обработке ответа: " + e.getMessage());
        }
    }
    
    /**
     * Парсит ответ от YandexGPT Responses API
     * Формат: { "id": "...", "output": [{ "content": [{ "text": "..." }] }], "error": null }
     */
    private Mono<String> parseYandexGPTResponsesAPIResponse(String responseBody, Long userId) {
        try {
            log.info("Парсинг ответа от YandexGPT Responses API (длина: {} символов)", 
                responseBody != null ? responseBody.length() : 0);
            log.debug("Полный ответ от API для парсинга: {}", responseBody);
            
            if (responseBody == null || responseBody.isEmpty()) {
                log.error("Пустой ответ от YandexGPT Responses API");
                return Mono.error(new RuntimeException("Пустой ответ от API"));
            }
            
            JsonNode responseJson = objectMapper.readTree(responseBody);
            
            // Проверяем наличие ошибки (только если error не null)
            if (responseJson.has("error") && !responseJson.get("error").isNull()) {
                JsonNode error = responseJson.get("error");
                String errorMessage = error.has("message") ? error.get("message").asText() : "Неизвестная ошибка";
                String errorCode = error.has("code") ? error.get("code").asText() : "UNKNOWN";
                
                // Логируем полную структуру ошибки
                log.error("YandexGPT Responses API вернул ошибку. Code: {}, Message: {}", errorCode, errorMessage);
                log.error("Полный объект error: {}", error.toString());
                
                // Пытаемся извлечь больше информации об ошибке
                if (error.has("details")) {
                    log.error("Детали ошибки: {}", error.get("details").toString());
                }
                if (error.has("status")) {
                    log.error("Статус ошибки: {}", error.get("status").asText());
                }
                
                // Логируем полный ответ для отладки
                log.error("Полный ответ с ошибкой: {}", responseBody);
                
                return Mono.error(new RuntimeException("YandexGPT Responses API Error [" + errorCode + "]: " + errorMessage));
            }
            
            // Основной формат Responses API - output (массив сообщений)
            if (responseJson.has("output") && responseJson.get("output").isArray()) {
                JsonNode outputArray = responseJson.get("output");
                if (outputArray.size() > 0) {
                    JsonNode firstOutput = outputArray.get(0);
                    if (firstOutput.has("content") && firstOutput.get("content").isArray()) {
                        JsonNode contentArray = firstOutput.get("content");
                        if (contentArray.size() > 0) {
                            JsonNode firstContent = contentArray.get(0);
                            if (firstContent.has("text")) {
                                String answer = firstContent.get("text").asText();
                                if (answer != null && !answer.isEmpty()) {
                                    log.info("Успешно получен ответ от YandexGPT Responses API (формат output). Длина ответа: {} символов", answer.length());
                                    conversationService.addMessage(userId, "assistant", answer);
                                    
                                    // Сохраняем response.id для контекста
                                    if (responseJson.has("id")) {
                                        String responseId = responseJson.get("id").asText();
                                        conversationService.setLastResponseId(userId, responseId);
                                        log.debug("Сохранен response ID для контекста: {}", responseId);
                                    }
                                    
                                    return Mono.just(answer);
                                }
                            }
                        }
                    }
                }
            }
            
            // Альтернативный формат: output_text (прямое поле)
            if (responseJson.has("output_text")) {
                String answer = responseJson.get("output_text").asText();
                if (answer != null && !answer.isEmpty()) {
                    log.info("Успешно получен ответ от YandexGPT Responses API. Длина ответа: {} символов", answer.length());
                    conversationService.addMessage(userId, "assistant", answer);
                    
                    // Сохраняем response.id для контекста (для следующего запроса)
                    if (responseJson.has("id")) {
                        String responseId = responseJson.get("id").asText();
                        conversationService.setLastResponseId(userId, responseId);
                        log.debug("Сохранен response ID для контекста: {}", responseId);
                    }
                    
                    return Mono.just(answer);
                }
            }
            
            // Альтернативный формат: output_message
            if (responseJson.has("output_message")) {
                JsonNode outputMessage = responseJson.get("output_message");
                if (outputMessage.has("content") && outputMessage.get("content").isArray()) {
                    JsonNode content = outputMessage.get("content");
                    if (content.size() > 0 && content.get(0).has("text")) {
                        String answer = content.get(0).get("text").asText();
                        if (answer != null && !answer.isEmpty()) {
                            log.info("Успешно получен ответ от YandexGPT Responses API (формат output_message). Длина ответа: {} символов", answer.length());
                            conversationService.addMessage(userId, "assistant", answer);
                            
                            // Сохраняем response.id для контекста
                            if (responseJson.has("id")) {
                                String responseId = responseJson.get("id").asText();
                                conversationService.setLastResponseId(userId, responseId);
                                log.debug("Сохранен response ID для контекста: {}", responseId);
                            }
                            
                            return Mono.just(answer);
                        }
                    }
                }
            }
            
            // Альтернативный формат: прямой text (для совместимости)
            if (responseJson.has("text")) {
                String answer = responseJson.get("text").asText();
                if (answer != null && !answer.isEmpty()) {
                    log.info("Успешно получен ответ от YandexGPT Responses API (формат text). Длина ответа: {} символов", answer.length());
                    conversationService.addMessage(userId, "assistant", answer);
                    
                    // Сохраняем response.id для контекста
                    if (responseJson.has("id")) {
                        String responseId = responseJson.get("id").asText();
                        conversationService.setLastResponseId(userId, responseId);
                        log.debug("Сохранен response ID для контекста: {}", responseId);
                    }
                    
                    return Mono.just(answer);
                }
            }
            
            // Логируем структуру ответа для отладки
            log.warn("Не найдено output_text, output_message или text в ответе от YandexGPT Responses API.");
            log.warn("Структура JSON ответа: {}", responseJson.toPrettyString());
            log.warn("Полный ответ (raw): {}", responseBody);
            return Mono.just("Ошибка: некорректная структура ответа от API. Проверьте логи для деталей.");
            
        } catch (JsonProcessingException e) {
            log.error("Ошибка при парсинге JSON ответа от YandexGPT Responses API. Ответ: {}", responseBody, e);
            return Mono.just("Ошибка при обработке ответа от API: " + e.getMessage());
        } catch (Exception e) {
            log.error("Неожиданная ошибка при парсинге ответа от YandexGPT Responses API", e);
            return Mono.just("Ошибка при обработке ответа: " + e.getMessage());
        }
    }
}

