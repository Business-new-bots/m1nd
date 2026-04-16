package com.example.m1nd.service;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class I18nService {
    private final MessageSource messageSource;

    public I18nService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String get(String languageCode, String key, Object... args) {
        Locale locale = toLocale(languageCode);
        return messageSource.getMessage(key, args, locale);
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
