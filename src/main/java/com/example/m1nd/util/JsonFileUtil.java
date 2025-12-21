package com.example.m1nd.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class JsonFileUtil {
    
    private final ObjectMapper objectMapper;
    
    public JsonFileUtil() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    public <T> void writeToFile(String filename, List<T> data) throws IOException {
        File file = new File(filename);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
        log.debug("Данные записаны в файл: {}", filename);
    }
    
    public <T> List<T> readFromFile(String filename, Class<T> clazz) throws IOException {
        File file = new File(filename);
        if (!file.exists()) {
            log.debug("Файл {} не существует, возвращаю пустой список", filename);
            return new ArrayList<>();  // Возвращаем изменяемый список
        }
        
        String content = new String(Files.readAllBytes(Paths.get(filename)));
        if (content.trim().isEmpty()) {
            log.debug("Файл {} пуст, возвращаю пустой список", filename);
            return new ArrayList<>();  // Возвращаем изменяемый список
        }
        
        // Убеждаемся, что возвращаем изменяемый ArrayList
        CollectionType listType = objectMapper.getTypeFactory()
            .constructCollectionType(ArrayList.class, clazz);
        List<T> result = objectMapper.readValue(content, listType);
        log.debug("Прочитано {} записей из файла {}", result.size(), filename);
        return result;
    }
}

