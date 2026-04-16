package com.example.m1nd.service;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

@Service
public class I18nService {
    private static final Map<String, String> DEFAULT_RU_MESSAGES = Map.ofEntries(
        Map.entry("start.welcome.text",
            "Добро пожаловать в ваш личный помощник по решению задач.\n\n" +
            "Чем могу помочь? Для вашего удобства доступны два формата поддержки:\n\n" +
            "AI Assistant — молниеносная скорость и точность от ИИ-асситентов.\n" +
            "Human Expert — индивидуальный подход и экспертиза от реальных специалистов.\n\n" +
            "Напишите суть вопроса, и я порекомендую, кто справится с ним лучше: нейросеть или живой профессионал."),
        Map.entry("menu.main.choose_assistant", "Выберите ассистента:"),
        Map.entry("menu.main.title", "Главное меню"),
        Map.entry("menu.reply.menu", "📋 Меню"),
        Map.entry("common.error.try_later", "Извините, произошла ошибка. Попробуйте позже."),
        Map.entry("common.error.short", "❌ Ошибка"),
        Map.entry("common.unknown_command", "❌ Неизвестная команда"),
        Map.entry("assistant.greeting.prefix.business", "Привет, я твой бизнес ИИ-агент. "),
        Map.entry("assistant.greeting.prefix.financial", "Привет, я твой финансовый ИИ-агент. "),
        Map.entry("assistant.greeting.prefix.thinking", "Привет, я твой ИИ-агент мыслитель. "),
        Map.entry("assistant.greeting.prefix.default", "Привет. "),
        Map.entry("assistant.greeting.body",
            "Обещаю, что наше общение с тобой будет конфеденциальным. " +
            "Я помогу тебе найти верное решение и интересующие тебя ответы. " +
            "А если не найду решение, отправлю твой вопрос основателю и он точно тебе поможет! Что ты хочешь спросить?")
    );

    private final MessageSource messageSource;

    public I18nService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String get(String languageCode, String key, Object... args) {
        Locale locale = toLocale(languageCode);
        try {
            return messageSource.getMessage(key, args, locale);
        } catch (NoSuchMessageException primaryError) {
            try {
                return messageSource.getMessage(key, args, new Locale("ru"));
            } catch (NoSuchMessageException ignored) {
                return DEFAULT_RU_MESSAGES.getOrDefault(key, key);
            }
        }
    }

    public Locale toLocale(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return new Locale("ru");
        }
        String code = languageCode.toLowerCase(Locale.ROOT);
        if (code.startsWith("en")) {
            return Locale.ENGLISH;
        }
        return new Locale("ru");
    }
}
