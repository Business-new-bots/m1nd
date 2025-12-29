package com.example.m1nd.service.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToolService {
    
    private final List<Tool> tools;
    
    /**
     * Получает список всех доступных инструментов в формате для LLM
     */
    public List<Map<String, Object>> getToolsForLLM() {
        return tools.stream()
            .map(tool -> {
                Map<String, Object> toolDef = new HashMap<>();
                toolDef.put("type", "function");
                
                Map<String, Object> function = new HashMap<>();
                function.put("name", tool.getName());
                function.put("description", tool.getDescription());
                function.put("parameters", tool.getParametersSchema());
                
                toolDef.put("function", function);
                return toolDef;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Выполняет инструмент по имени
     */
    public String executeTool(String toolName, Map<String, Object> parameters) {
        Tool tool = tools.stream()
            .filter(t -> t.getName().equals(toolName))
            .findFirst()
            .orElse(null);
        
        if (tool == null) {
            log.warn("Инструмент {} не найден", toolName);
            return "Ошибка: инструмент " + toolName + " не найден";
        }
        
        try {
            log.info("Выполняю инструмент: {} с параметрами: {}", toolName, parameters);
            return tool.execute(parameters);
        } catch (Exception e) {
            log.error("Ошибка при выполнении инструмента {}", toolName, e);
            return "Ошибка при выполнении инструмента: " + e.getMessage();
        }
    }
}

