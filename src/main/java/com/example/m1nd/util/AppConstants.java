package com.example.m1nd.util;

/**
 * Константы приложения
 */
public final class AppConstants {
    
    // Вопросы
    public static final int DEFAULT_QUESTIONS_PER_SESSION = 20;
    public static final int MIN_QUESTIONS_PER_SESSION = 1;
    public static final int MAX_QUESTIONS_PER_SESSION = 50;
    
    // Оценки
    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 10;
    public static final int DEFAULT_SCORE_ON_ERROR = 5;
    
    // API
    public static final int API_RESPONSE_TIMEOUT_MINUTES = 5;
    public static final int API_MAX_IN_MEMORY_SIZE_MB = 10;
    public static final int API_MAX_TOKENS = 4000;
    public static final double API_TEMPERATURE = 0.5;
    public static final int API_RETRY_ATTEMPTS = 2;
    public static final int API_RETRY_DELAY_SECONDS = 1;
    
    // Формат времени
    public static final String TIME_FORMAT = "HH:mm";
    
    private AppConstants() {
        // Utility class
    }
}

