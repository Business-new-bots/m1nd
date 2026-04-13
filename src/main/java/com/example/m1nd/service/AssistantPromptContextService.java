package com.example.m1nd.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AssistantPromptContextService {

    // assistantCode: business | financial | thinking
    private final Map<Long, String> assistantByUser = new ConcurrentHashMap<>();
    // modeCode: text | question | meeting
    private final Map<Long, String> modeByUser = new ConcurrentHashMap<>();

    public void setAssistant(Long userId, String assistantCode) {
        if (userId == null || assistantCode == null) {
            return;
        }
        assistantByUser.put(userId, assistantCode);
    }

    public void setMode(Long userId, String modeCode) {
        if (userId == null || modeCode == null) {
            return;
        }
        modeByUser.put(userId, modeCode);
    }

    public void clear(Long userId) {
        if (userId == null) {
            return;
        }
        assistantByUser.remove(userId);
        modeByUser.remove(userId);
    }

    public void clearMode(Long userId) {
        if (userId == null) {
            return;
        }
        modeByUser.remove(userId);
    }

    public String getAssistant(Long userId) {
        if (userId == null) {
            return null;
        }
        return assistantByUser.get(userId);
    }

    public String getMode(Long userId) {
        if (userId == null) {
            return null;
        }
        return modeByUser.get(userId);
    }

    public String getTopicPrompt(Long userId) {
        if (userId == null) {
            return "";
        }

        String assistantCode = assistantByUser.get(userId);
        String modeCode = modeByUser.get(userId);

        String assistantPrompt = assistantPrompt(assistantCode);
        String modePrompt = modePrompt(modeCode);

        if (assistantPrompt.isBlank() && modePrompt.isBlank()) {
            return "";
        }

        return (assistantPrompt + (modePrompt.isBlank() ? "" : "\n\n" + modePrompt)).trim();
    }

    private String assistantPrompt(String assistantCode) {
        if (assistantCode == null) {
            return "";
        }

        return switch (assistantCode) {
            case "business" -> """
                ТЫ: Бизнес ИИ-ассистент.
                Твоя задача — помогать пользователю решать бизнес-задачи: стратегия, продукт, маркетинг, продажи, операционные процессы, управление командой.
                Отвечай структурировано и практично:
                - уточни контекст пользователя (цели, аудитория, ограничения)
                - предложи план действий по шагам
                - добавь метрики/критерии, по которым можно измерить результат
                - при необходимости приводи короткие примеры формулировок (сообщения клиенту, шаблон отчета)
                """;
            case "financial" -> """
                ТЫ: Финансовый ИИ-ассистент.
                Помогай пользователю разбираться в финансах: личный бюджет, кредиты/расходы, планирование, базовая логика инвестиций.
                Важно: это не индивидуальная инвестиционная/финансовая рекомендация. Если нужны конкретные решения — предложи пользователю собрать данные и обсудить с профессионалом.
                Отвечай так:
                - объясняй термины простыми словами
                - предложи расчёт/сценарии (хотя бы на уровне шаблона)
                - дай чек-лист, что уточнить перед решением
                """;
            case "thinking" -> """
                ТЫ: ИИ ассистент по мышлению.
                Помогай пользователю мыслить яснее: формулировать проблему, строить аргументы, проверять допущения, разбирать варианты, принимать решения.
                Используй понятные фреймворки:
                - плюсы/минусы и риски
                - вопросы, которые надо задать самому себе
                - варианты решения и критерии выбора
                - если данных не хватает — сначала задай уточняющие вопросы
                """;
            default -> "";
        };
    }

    private String modePrompt(String modeCode) {
        if (modeCode == null) {
            return "";
        }

        return switch (modeCode) {
            case "text" -> """
                Режим общения: Текстовый помощник.
                Дай готовый ответ, шаги и формулировки, которые пользователь может сразу использовать.
                """;
            case "question" -> """
                Режим общения: Получить рекомендацию от основателя.
                Сформируй:
                - краткое резюме проблемы
                - точные вопросы специалисту (списком)
                - какие данные/документы стоит подготовить
                """;
            case "meeting" -> """
                Режим общения: Онлайн-встреча с основателем.
                Сформируй повестку встречи:
                - цель встречи
                - список вопросов
                - ожидаемые результаты/что проверить в конце разговора
                """;
            default -> "";
        };
    }
}

