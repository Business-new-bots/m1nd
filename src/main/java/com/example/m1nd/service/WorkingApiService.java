package com.example.m1nd.service;

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
    
    private final PromptService promptService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient client;

    public WorkingApiService(PromptService promptService) {
        this.promptService = promptService;
        this.client = createWebClient();
    }

    private WebClient createWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMinutes(5))
                .keepAlive(true)
                .followRedirect(true);

        return WebClient.builder()
                .baseUrl(API_BASE_URL)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs()
                    .maxInMemorySize(10 * 1024 * 1024))
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
        logger.debug("Отправляем запрос к GPT-Chatbot API для вопроса: {}", logQuestion);

        Map<String, Object> requestBody = buildRequestBody(questionText);

        return client.post()
                .uri(API_ENDPOINT)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .timeout(Duration.ofMinutes(5))
                .map(this::convertDataBufferToString)
                .flatMap(this::splitIntoLines)
                .filter(line -> line != null && !line.trim().isEmpty())
                .map(this::removeAnsiCodes)
                .filter(line -> line.startsWith(DATA_PREFIX))
                .flatMap(this::parseStreamChunk)
                .collectList()
                .map(this::combineChunks)
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                        .filter(throwable -> {
                            String message = throwable.getMessage();
                            String className = throwable.getClass().getName();
                            
                            if (message != null && (
                                message.contains("Connection reset") ||
                                message.contains("Connection reset by peer") ||
                                message.contains("recvAddress")
                            )) {
                                return true;
                            }
                            
                            if (className.contains("ClosedChannelException") ||
                                className.contains("NativeIoException") ||
                                className.contains("SSLHandshakeException") ||
                                className.contains("StacklessSSLHandshakeException")) {
                                return true;
                            }
                            
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
        
        // Получаем системный промпт из prompt.txt
        String systemPrompt = promptService.getPrompt();
        
        // Формируем сообщения: системный промпт + вопрос пользователя
        List<Map<String, String>> messages = List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", questionText)
        );
        
        requestBody.put("messages", messages);
        requestBody.put("stream", true);
        requestBody.put("model", "gpt-4.1-mini");
        requestBody.put("temperature", 0.7);
        requestBody.put("presence_penalty", 0);
        requestBody.put("frequency_penalty", 0);
        requestBody.put("top_p", 1);
        requestBody.put("max_tokens", 2000);
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
        
        if (!jsonData.startsWith("{") || !jsonData.endsWith("}")) {
            logger.debug("Пропускаем неполный JSON чанк: {}", jsonData.length() > 100 ? jsonData.substring(0, 100) + "..." : jsonData);
            return Mono.empty();
        }
        
        try {
            Map<String, Object> chunk = objectMapper.readValue(jsonData, Map.class);
            
            if (chunk.containsKey("error")) {
                Map<String, Object> error = (Map<String, Object>) chunk.get("error");
                String errorMsg = String.valueOf(error.get("message"));
                logger.error("API вернул ошибку: {}", errorMsg);
                return Mono.error(new RuntimeException("API Error: " + errorMsg));
            }
            
            return extractContentFromChunk(chunk);
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            logger.debug("Неполный или невалидный JSON чанк, пропускаем: {}", jsonData.length() > 100 ? jsonData.substring(0, 100) + "..." : jsonData);
            return Mono.empty();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.debug("Ошибка парсинга JSON чанка, пропускаем: {}", e.getMessage());
            return Mono.empty();
        } catch (RuntimeException e) {
            return Mono.error(e);
        } catch (Exception e) {
            logger.warn("Неожиданная ошибка при парсинге stream чанка: {}", jsonData.length() > 100 ? jsonData.substring(0, 100) + "..." : jsonData, e);
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
            logger.warn("Пустой ответ из stream");
            return "❌ Не удалось получить ответ от API. Ответ пуст.";
        }
        logger.debug("Успешно получен stream ответ от API (длина: {})", result.length());
        return result;
    }
    
    private Mono<String> handleError(Throwable error) {
        logger.error("Ошибка запроса к API", error);
        return Mono.just("❌ Ошибка сети при запросе к AI: " + error.getMessage());
    }
}
