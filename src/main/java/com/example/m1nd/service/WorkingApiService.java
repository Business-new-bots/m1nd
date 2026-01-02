package com.example.m1nd.service;

import com.example.m1nd.model.AnswerEvaluation;
import com.example.m1nd.util.AppConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkingApiService {

    private static final Logger logger = LoggerFactory.getLogger(WorkingApiService.class);
    private static final String API_BASE_URL = "https://chat.gpt-chatbot.ru";
    private static final String API_ENDPOINT = "/api/openai/v1/chat/completions";
    private static final String DATA_PREFIX = "data: ";
    private static final String DONE_MARKER = "[DONE]";
    private static final String ANSI_ESCAPE_REGEX = "\u001B\\[[;\\d]*m";
    
    private final WebClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WorkingApiService() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMinutes(AppConstants.API_RESPONSE_TIMEOUT_MINUTES))
                .keepAlive(true)
                .followRedirect(true);

        this.client = WebClient.builder()
                .baseUrl(API_BASE_URL)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs()
                    .maxInMemorySize(AppConstants.API_MAX_IN_MEMORY_SIZE_MB * 1024 * 1024))
                .defaultHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 YaBrowser/25.8.0.0 Safari/537.36")
                .defaultHeader("Accept", "application/json, text/event-stream")
                .defaultHeader("Accept-Language", "ru,en;q=0.9,la;q=0.8,sr;q=0.7,bg;q=0.6")
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Referer", "https://chat.gpt-chatbot.ru/")
                .defaultHeader("Origin", "https://chat.gpt-chatbot.ru")
                .defaultHeader("Priority", "u=1, i")
                .defaultHeader("Sec-Fetch-Dest", "empty")
                .defaultHeader("Sec-Fetch-Mode", "cors")
                .defaultHeader("Sec-Fetch-Site", "same-origin")
                .defaultHeader("Sec-CH-UA", "\"Not)A;Brand\";v=\"8\", \"Chromium\";v=\"138\", \"YaBrowser\";v=\"25.8\", \"Yowser\";v=\"2.5\"")
                .defaultHeader("Sec-CH-UA-Mobile", "?0")
                .defaultHeader("Sec-CH-UA-Platform", "\"Linux\"")
                .build();
    }

    public Mono<String> getAnswer(String questionText) {
        String logQuestion = questionText.length() > 50 
            ? questionText.substring(0, 50) + "..." 
            : questionText;
        logger.debug("Sending request to GPT-Chatbot API for question: {}", logQuestion);

        Map<String, Object> requestBody = buildRequestBody(questionText);

        return client.post()
                .uri(API_ENDPOINT)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .timeout(Duration.ofMinutes(AppConstants.API_RESPONSE_TIMEOUT_MINUTES))
                .map(this::convertDataBufferToString)
                .flatMap(this::splitIntoLines)
                .filter(line -> line != null && !line.trim().isEmpty())
                .map(this::removeAnsiCodes)
                .filter(line -> line.startsWith(DATA_PREFIX))
                .flatMap(this::parseStreamChunk)
                .collectList()
                .map(this::combineChunks)
                .retryWhen(Retry.backoff(AppConstants.API_RETRY_ATTEMPTS, 
                        Duration.ofSeconds(AppConstants.API_RETRY_DELAY_SECONDS))
                        .filter(throwable -> {
                            // Проверяем различные типы сетевых ошибок
                            String message = throwable.getMessage();
                            String className = throwable.getClass().getName();
                            
                            // Connection reset ошибки
                            if (message != null && (
                                message.contains("Connection reset") ||
                                message.contains("Connection reset by peer") ||
                                message.contains("recvAddress")
                            )) {
                                return true;
                            }
                            
                            // Закрытые каналы и SSL ошибки
                            if (className.contains("ClosedChannelException") ||
                                className.contains("NativeIoException") ||
                                className.contains("SSLHandshakeException") ||
                                className.contains("StacklessSSLHandshakeException")) {
                                return true;
                            }
                            
                            // Другие сетевые ошибки
                            if (message != null && (
                                message.contains("Connection closed") ||
                                message.contains("Connection refused") ||
                                message.contains("timeout")
                            )) {
                                return true;
                            }
                            
                            return false;
                        }))
                .onErrorResume(this::handleError);
    }
    
    private Map<String, Object> buildRequestBody(String questionText) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("messages", List.of(Map.of("role", "user", "content", questionText)));
        requestBody.put("stream", true);
        requestBody.put("model", "gpt-4.1-mini");
        requestBody.put("temperature", AppConstants.API_TEMPERATURE);
        requestBody.put("presence_penalty", 0);
        requestBody.put("frequency_penalty", 0);
        requestBody.put("top_p", 1);
        requestBody.put("max_tokens", AppConstants.API_MAX_TOKENS);
        return requestBody;
    }
    
    private String convertDataBufferToString(DataBuffer dataBuffer) {
        byte[] bytes = new byte[dataBuffer.readableByteCount()];
        dataBuffer.read(bytes);
        DataBufferUtils.release(dataBuffer);
        return new String(bytes, StandardCharsets.UTF_8);
    }
    
    private Flux<String> splitIntoLines(String content) {
        return Flux.fromArray(content.split("\r?\n"));
    }
    
    private String removeAnsiCodes(String line) {
        return line.replaceAll(ANSI_ESCAPE_REGEX, "").trim();
    }
    
    private Mono<String> parseStreamChunk(String line) {
        String jsonData = line.substring(DATA_PREFIX.length()).trim();
        
        if (DONE_MARKER.equals(jsonData) || jsonData.isEmpty()) {
            return Mono.empty();
        }
        
        // Проверяем, что JSON выглядит завершенным (хотя бы начинается и заканчивается правильно)
        if (!jsonData.startsWith("{") || !jsonData.endsWith("}")) {
            logger.debug("Skipping incomplete JSON chunk: {}", jsonData.length() > 100 ? jsonData.substring(0, 100) + "..." : jsonData);
            return Mono.empty();
        }
        
        try {
            Map<String, Object> chunk = objectMapper.readValue(jsonData, Map.class);
            
            if (chunk.containsKey("error")) {
                Map<String, Object> error = (Map<String, Object>) chunk.get("error");
                String errorMsg = String.valueOf(error.get("message"));
                logger.error("API returned error: {}", errorMsg);
                return Mono.error(new RuntimeException("API Error: " + errorMsg));
            }
            
            return extractContentFromChunk(chunk);
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            // JSON обрывается или невалидный - это нормально для потоков, просто пропускаем
            logger.debug("Incomplete or invalid JSON chunk received, skipping: {}", jsonData.length() > 100 ? jsonData.substring(0, 100) + "..." : jsonData);
            return Mono.empty();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // Другие ошибки парсинга JSON - пропускаем чанк
            logger.debug("Error parsing JSON chunk, skipping: {}", e.getMessage());
            return Mono.empty();
        } catch (RuntimeException e) {
            return Mono.error(e);
        } catch (Exception e) {
            logger.warn("Unexpected error parsing stream chunk: {}", jsonData.length() > 100 ? jsonData.substring(0, 100) + "..." : jsonData, e);
            return Mono.empty();
        }
    }
    
    @SuppressWarnings("unchecked")
    private Mono<String> extractContentFromChunk(Map<String, Object> chunk) {
        var choices = (List<Map<String, Object>>) chunk.get("choices");
        if (choices != null && !choices.isEmpty()) {
            var choice = choices.get(0);
            var delta = (Map<String, Object>) choice.get("delta");
            if (delta != null && delta.containsKey("content")) {
                return Mono.just(String.valueOf(delta.get("content")));
            }
        }
        return Mono.empty();
    }
    
    private String combineChunks(List<String> chunks) {
        StringBuilder fullContent = new StringBuilder();
        for (String chunk : chunks) {
            fullContent.append(chunk);
        }
        String result = fullContent.toString();
        if (result.isEmpty()) {
            logger.warn("Empty response from stream");
            return "❌ Не удалось получить ответ от API. Ответ пуст.";
        }
        logger.debug("Successfully received stream answer from API (length: {})", result.length());
        return result;
    }
    
    private Mono<String> handleError(Throwable error) {
        logger.error("API request failed", error);
        return Mono.just("❌ Ошибка сети при запросе к AI: " + error.getMessage());
    }

    /**
     * Проверяет, относится ли вопрос к программированию или IT-технологиям
     * 
     * @param questionText текст вопроса для проверки
     * @return Mono<Boolean> true если вопрос относится к программированию, false иначе
     */
    public Mono<Boolean> isProgrammingRelated(String questionText) {
        String prompt = String.format(
            "Проанализируй следующий вопрос и определи, относится ли он к программированию, разработке ПО, компьютерным наукам или IT-технологиям.\n\n" +
            "Вопрос: %s\n\n" +
            "Ответь только одним словом: ДА или НЕТ. Не добавляй никаких пояснений.",
            questionText
        );

        return getAnswer(prompt)
            .map(response -> {
                String lowerResponse = response.toLowerCase().trim();
                // Проверяем различные варианты положительного ответа
                return lowerResponse.contains("да") || 
                       lowerResponse.contains("yes") || 
                       lowerResponse.startsWith("да") ||
                       lowerResponse.startsWith("yes");
            })
            .timeout(Duration.ofSeconds(10))
            .onErrorReturn(true); // В случае ошибки разрешаем вопрос, чтобы не блокировать пользователя
    }
    
    /**
     * Оценивает ответ пользователя по 10-бальной шкале и предоставляет дополнения
     * @param questionText Текст вопроса
     * @param userAnswerText Ответ пользователя
     * @return Mono с оценкой и дополнениями
     */
    public Mono<AnswerEvaluation> evaluateAnswer(
            String questionText, String userAnswerText) {
        String prompt = String.format(
            "Вопрос: %s\n\nОтвет пользователя: %s\n\n" +
            "Оцени ответ пользователя по 10-бальной шкале, где:\n" +
            "- 0-2: Полностью неверный ответ или отсутствие ответа\n" +
            "- 3-4: Неверный ответ с частичным пониманием\n" +
            "- 5-6: Частично верный ответ с некоторыми неточностями\n" +
            "- 7-8: Верный ответ с небольшими недочетами\n" +
            "- 9-10: Полностью верный и полный ответ\n\n" +
            "Ответь в следующем формате (строго соблюдай формат):\n" +
            "ОЦЕНКА: [число от 0 до 10]\n" +
            "ДОПОЛНЕНИЯ: [что не было упомянуто в ответе пользователя, что можно добавить или улучшить. " +
            "Если ответ полный и верный (9-10), напиши 'Ответ полный и верный'. " +
            "Если ответ неверный, укажи основные ошибки и что нужно исправить]",
            questionText, userAnswerText
        );

        return getAnswer(prompt)
                .map(response -> parseEvaluationResponse(response))
                .onErrorReturn(new AnswerEvaluation(
                    AppConstants.DEFAULT_SCORE_ON_ERROR, 
                    "Не удалось оценить ответ. Попробуйте еще раз."));
    }
    
    private AnswerEvaluation parseEvaluationResponse(String response) {
        try {
            int score = AppConstants.DEFAULT_SCORE_ON_ERROR;
            String feedback = "";
            
            // Извлекаем оценку
            String[] lines = response.split("\n");
            for (String line : lines) {
                if (line.trim().startsWith("ОЦЕНКА:") || line.trim().startsWith("ОЦЕНКА")) {
                    String scoreStr = line.replaceAll("ОЦЕНКА:?", "").trim().replaceAll("[^0-9]", "");
                    if (!scoreStr.isEmpty()) {
                        score = Integer.parseInt(scoreStr);
                        score = Math.max(AppConstants.MIN_SCORE, Math.min(AppConstants.MAX_SCORE, score));
                    }
                } else if (line.trim().startsWith("ДОПОЛНЕНИЯ:") || line.trim().startsWith("ДОПОЛНЕНИЯ")) {
                    feedback = line.replaceAll("ДОПОЛНЕНИЯ:?", "").trim();
                }
            }
            
            // Если не нашли в формате, пытаемся извлечь число из всего ответа
            if (score == AppConstants.DEFAULT_SCORE_ON_ERROR && feedback.isEmpty()) {
                String cleaned = response.trim().replaceAll("[^0-9]", "");
                if (!cleaned.isEmpty()) {
                    score = Integer.parseInt(cleaned);
                    score = Math.max(AppConstants.MIN_SCORE, Math.min(AppConstants.MAX_SCORE, score));
                }
                // Берем весь ответ как дополнения (кроме числа)
                feedback = response.replaceAll("\\d+", "").trim();
                if (feedback.length() > 500) {
                    feedback = feedback.substring(0, 500) + "...";
                }
            }
            
            // Если дополнения не найдены, но есть текст после оценки
            if (feedback.isEmpty() && response.length() > 20) {
                // Пытаемся найти текст после "ОЦЕНКА" или числа
                String[] parts = response.split("(ОЦЕНКА|\\d+)", 2);
                if (parts.length > 1) {
                    feedback = parts[1].trim();
                    if (feedback.length() > 500) {
                        feedback = feedback.substring(0, 500) + "...";
                    }
                }
            }
            
            // Если все еще нет дополнений, используем дефолтное сообщение
            if (feedback.isEmpty()) {
                if (score >= 9) {
                    feedback = "Ответ полный и верный!";
                } else if (score >= 7) {
                    feedback = "Ответ верный, но можно добавить больше деталей.";
                } else if (score >= 5) {
                    feedback = "Ответ частично верный, но есть неточности и неполнота.";
                } else {
                    feedback = "Ответ требует значительных улучшений и дополнений.";
                }
            }

            return new AnswerEvaluation(score, feedback);
        } catch (Exception e) {
            logger.warn("Failed to parse evaluation response: {}", response, e);
            return new AnswerEvaluation(
                AppConstants.DEFAULT_SCORE_ON_ERROR, 
                "Не удалось обработать оценку. Попробуйте еще раз.");
        }
    }
}
