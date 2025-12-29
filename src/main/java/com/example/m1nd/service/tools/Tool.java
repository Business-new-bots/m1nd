package com.example.m1nd.service.tools;

import java.util.Map;

/**
 * Интерфейс для инструментов, которые может вызывать LLM
 */
public interface Tool {
    
    /**
     * Возвращает название инструмента
     */
    String getName();
    
    /**
     * Возвращает описание инструмента для LLM
     */
    String getDescription();
    
    /**
     * Возвращает схему параметров в формате JSON Schema
     */
    Map<String, Object> getParametersSchema();
    
    /**
     * Выполняет инструмент с переданными параметрами
     */
    String execute(Map<String, Object> parameters);
}

