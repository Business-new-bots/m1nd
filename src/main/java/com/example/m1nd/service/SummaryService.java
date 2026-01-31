package com.example.m1nd.service;

import com.example.m1nd.model.UserSessionSummary;
import com.example.m1nd.repository.UserSessionSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryService {
    
    private final ConversationService conversationService;
    private final LLMService llmService;
    private final UserSessionSummaryRepository summaryRepository;
    
    /**
     * Создаёт сводку диалога пользователя и сохраняет в БД
     */
    @Transactional
    public Mono<String> createAndSaveSummary(Long userId, String username) {
        log.info("Начинаем создание сводки для пользователя {}", userId);
        
        // Получаем историю диалога
        List<Map<String, String>> history = conversationService.getHistory(userId);
        
        if (history == null || history.isEmpty()) {
            log.warn("История диалога пуста для пользователя {}", userId);
            return Mono.just("История диалога пуста. Нет данных для сводки.");
        }
        
        // Извлекаем пары вопрос-ответ
        List<QuestionAnswerPair> qaPairs = extractQuestionAnswerPairs(history);
        
        if (qaPairs.isEmpty()) {
            log.warn("Не найдено пар вопрос-ответ для пользователя {}", userId);
            return Mono.just("Не найдено вопросов и ответов для сводки.");
        }
        
        // Ограничиваем количество пар (чтобы не переполнить контекст)
        final List<QuestionAnswerPair> finalQaPairs;
        if (qaPairs.size() > 20) {
            finalQaPairs = qaPairs.subList(qaPairs.size() - 20, qaPairs.size());
            log.info("Ограничили историю до последних 20 пар вопрос-ответ");
        } else {
            finalQaPairs = qaPairs;
        }
        
        // Формируем промпт для свёртки
        String summarizationPrompt = buildSummarizationPrompt(finalQaPairs);
        
        // Отправляем в YandexGPT для свёртки
        return llmService.summarizeConversation(summarizationPrompt)
            .flatMap(summaryText -> {
                try {
                    // Парсим ответ: ищем "Вопрос:" и "Ответ:"
                    SummaryResult result = parseSummaryResponse(summaryText);
                    
                    if (result.question == null || result.answer == null) {
                        log.warn("Не удалось распарсить сводку. Полный ответ: {}", summaryText);
                        return Mono.just("Ошибка: не удалось извлечь сводку из ответа агента.");
                    }
                    
                    // Определяем временные границы сессии
                    LocalDateTime sessionStart = finalQaPairs.get(0).timestamp;
                    LocalDateTime sessionEnd = finalQaPairs.get(finalQaPairs.size() - 1).timestamp;
                    
                    // Сохраняем в БД
                    UserSessionSummary summary = new UserSessionSummary();
                    summary.setUserId(userId);
                    summary.setUsername(username);
                    summary.setSummaryQuestion(result.question);
                    summary.setSummaryAnswer(result.answer);
                    summary.setSessionStartAt(sessionStart);
                    summary.setSessionEndAt(sessionEnd);
                    summary.setCreatedAt(LocalDateTime.now());
                    
                    summaryRepository.save(summary);
                    
                    log.info("Сводка сохранена для пользователя {}: вопрос='{}', ответ='{}'", 
                        userId, result.question, result.answer);
                    
                    return Mono.just("✅ Сводка создана и сохранена!");
                } catch (Exception e) {
                    log.error("Ошибка при сохранении сводки", e);
                    return Mono.just("Ошибка при сохранении сводки: " + e.getMessage());
                }
            })
            .onErrorResume(error -> {
                log.error("Ошибка при создании сводки", error);
                return Mono.just("Ошибка при создании сводки: " + error.getMessage());
            });
    }
    
    /**
     * Извлекает пары вопрос-ответ из истории диалога
     */
    private List<QuestionAnswerPair> extractQuestionAnswerPairs(List<Map<String, String>> history) {
        List<QuestionAnswerPair> pairs = new ArrayList<>();
        String currentQuestion = null;
        LocalDateTime questionTime = null;
        
        for (Map<String, String> message : history) {
            String role = message.get("role");
            String content = message.get("content");
            
            if ("user".equals(role) && content != null && !content.trim().isEmpty()) {
                // Сохраняем вопрос
                currentQuestion = content.trim();
                questionTime = LocalDateTime.now(); // В реальности можно брать из метаданных, если есть
            } else if ("assistant".equals(role) && currentQuestion != null && content != null && !content.trim().isEmpty()) {
                // Нашли ответ на вопрос
                pairs.add(new QuestionAnswerPair(currentQuestion, content.trim(), questionTime));
                currentQuestion = null;
                questionTime = null;
            }
        }
        
        return pairs;
    }
    
    /**
     * Формирует промпт для свёртки диалога
     */
    private String buildSummarizationPrompt(List<QuestionAnswerPair> qaPairs) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Ты — аналитик диалогов.\n\n");
        prompt.append("Ниже список вопросов пользователя и ответов ассистента.\n\n");
        prompt.append("Твоя задача:\n");
        prompt.append("1) Сформулировать ОДИН общий/главный вопрос, который объединяет эти вопросы.\n");
        prompt.append("2) Сформулировать ОДИН общий/главный ответ, который обобщает ответы.\n\n");
        prompt.append("Выведи строго в формате:\n");
        prompt.append("Вопрос: <краткий общий вопрос>\n");
        prompt.append("Ответ: <краткий общий ответ>\n\n");
        prompt.append("---\n\n");
        
        for (int i = 0; i < qaPairs.size(); i++) {
            QuestionAnswerPair pair = qaPairs.get(i);
            prompt.append("[").append(i + 1).append("] ВОПРОС: ").append(pair.question).append("\n");
            prompt.append("    ОТВЕТ: ").append(pair.answer).append("\n\n");
        }
        
        return prompt.toString();
    }
    
    /**
     * Парсит ответ от агента, извлекая общий вопрос и ответ
     */
    private SummaryResult parseSummaryResponse(String response) {
        SummaryResult result = new SummaryResult();
        
        // Ищем паттерны "Вопрос: ..." и "Ответ: ..."
        Pattern questionPattern = Pattern.compile("Вопрос:\\s*(.+?)(?=\\n|Ответ:|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Pattern answerPattern = Pattern.compile("Ответ:\\s*(.+?)(?=\\n|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        
        Matcher questionMatcher = questionPattern.matcher(response);
        Matcher answerMatcher = answerPattern.matcher(response);
        
        if (questionMatcher.find()) {
            result.question = questionMatcher.group(1).trim();
        }
        
        if (answerMatcher.find()) {
            result.answer = answerMatcher.group(1).trim();
        }
        
        // Если не нашли по паттерну, пытаемся найти вручную
        if (result.question == null || result.answer == null) {
            String[] lines = response.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.toLowerCase().startsWith("вопрос:")) {
                    result.question = line.substring("вопрос:".length()).trim();
                } else if (line.toLowerCase().startsWith("ответ:")) {
                    result.answer = line.substring("ответ:".length()).trim();
                }
            }
        }
        
        return result;
    }
    
    /**
     * Получает сводки пользователя за указанную дату
     */
    public List<UserSessionSummary> getSummariesByUserAndDate(Long userId, LocalDate date) {
        return summaryRepository.findByUserIdAndDate(userId, date);
    }
    
    /**
     * Получает список активных пользователей за указанную дату
     */
    public List<UserActivityInfo> getActiveUsersByDate(LocalDate date) {
        List<Object[]> results = summaryRepository.findDistinctUsersByDate(date);
        List<UserActivityInfo> users = new ArrayList<>();
        
        for (Object[] row : results) {
            UserActivityInfo info = new UserActivityInfo();
            info.userId = (Long) row[0];
            info.username = (String) row[1];
            users.add(info);
        }
        
        return users;
    }
    
    // Вспомогательные классы
    private static class QuestionAnswerPair {
        String question;
        String answer;
        LocalDateTime timestamp;
        
        QuestionAnswerPair(String question, String answer, LocalDateTime timestamp) {
            this.question = question;
            this.answer = answer;
            this.timestamp = timestamp;
        }
    }
    
    private static class SummaryResult {
        String question;
        String answer;
    }
    
    public static class UserActivityInfo {
        public Long userId;
        public String username;
    }
}
