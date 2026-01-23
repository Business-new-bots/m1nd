package com.example.m1nd.service;

import com.example.m1nd.model.User;
import com.example.m1nd.util.JsonFileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    
    private final JsonFileUtil jsonFileUtil;
    
    @Value("${app.data.users-file}")
    private String usersFile;
    
    @PostConstruct
    public void init() {
        log.info("UserService инициализирован. Файл пользователей: {}", usersFile);
        
        // Проверяем абсолютный путь к файлу
        try {
            java.io.File file = new java.io.File(usersFile);
            log.info("Абсолютный путь к файлу пользователей: {}", file.getAbsolutePath());
            log.info("Файл существует: {}", file.exists());
            
            // Если файл не существует, создаем его из resources
            if (!file.exists()) {
                log.info("Файл {} не существует, пытаемся создать из resources", usersFile);
                try {
                    // Пытаемся скопировать из resources
                    java.io.InputStream resourceStream = getClass().getClassLoader()
                        .getResourceAsStream("users.json");
                    if (resourceStream != null) {
                        String content = new String(resourceStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        java.nio.file.Files.write(java.nio.file.Paths.get(usersFile), content.getBytes());
                        log.info("Файл {} создан из resources", usersFile);
                    } else {
                        // Создаем пустой файл
                        List<User> emptyList = new ArrayList<>();
                        jsonFileUtil.writeToFile(usersFile, emptyList);
                        log.info("Создан пустой файл {}", usersFile);
                    }
                } catch (Exception e) {
                    log.warn("Не удалось создать файл из resources: {}", e.getMessage());
                    // Создаем пустой файл
                    try {
                        List<User> emptyList = new ArrayList<>();
                        jsonFileUtil.writeToFile(usersFile, emptyList);
                        log.info("Создан пустой файл {}", usersFile);
                    } catch (IOException ioException) {
                        log.error("Не удалось создать файл {}", usersFile, ioException);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Ошибка при проверке пути к файлу: {}", e.getMessage());
        }
        
        try {
            List<User> users = jsonFileUtil.readFromFile(usersFile, User.class);
            log.info("Загружено {} пользователей", users.size());
        } catch (Exception e) {
            log.warn("Не удалось загрузить пользователей при инициализации: {}", e.getMessage());
        }
    }
    
    public void registerUser(Update update) {
        try {
            Long userId = update.getMessage().getFrom().getId();
            String username = update.getMessage().getFrom().getUserName();
            String firstName = update.getMessage().getFrom().getFirstName();
            String lastName = update.getMessage().getFrom().getLastName();
            
            List<User> users = jsonFileUtil.readFromFile(usersFile, User.class);
            
            // Проверяем, существует ли пользователь
            Optional<User> existingUser = users.stream()
                .filter(u -> u.getUserId().equals(userId))
                .findFirst();
            
            LocalDateTime now = LocalDateTime.now();
            
            if (existingUser.isEmpty()) {
                // Создаем нового пользователя
                User newUser = new User();
                newUser.setUserId(userId);
                newUser.setUsername(username);
                newUser.setFirstName(firstName);
                newUser.setLastName(lastName);
                newUser.setRegisteredAt(now);
                newUser.setFirstActivityAt(now);
                newUser.setLastActivityAt(now);
                newUser.setQuestionsCount(0);
                newUser.setTotalMessages(0);
                newUser.setSessionsCount(1);
                newUser.setIsReturningUser(false);
                
                users.add(newUser);
                jsonFileUtil.writeToFile(usersFile, users);
                log.info("Зарегистрирован новый пользователь: {}", userId);
            } else {
                // Обновляем существующего пользователя
                User user = existingUser.get();
                updateUserActivity(user, now);
                
                // Обновляем данные пользователя, если они изменились
                user.setUsername(username);
                user.setFirstName(firstName);
                user.setLastName(lastName);
                
                jsonFileUtil.writeToFile(usersFile, users);
                log.debug("Обновлена активность пользователя {}: {}", userId, now);
            }
        } catch (IOException e) {
            log.error("Ошибка при регистрации пользователя", e);
        }
    }
    
    /**
     * Отслеживает активность пользователя (любое взаимодействие)
     */
    public void trackUserActivity(Long userId) {
        try {
            List<User> users = jsonFileUtil.readFromFile(usersFile, User.class);
            
            users.stream()
                .filter(u -> u.getUserId().equals(userId))
                .findFirst()
                .ifPresent(user -> {
                    LocalDateTime now = LocalDateTime.now();
                    updateUserActivity(user, now);
                    
                    // Увеличиваем счетчик сообщений
                    user.setTotalMessages(user.getTotalMessages() + 1);
                    
                    try {
                        jsonFileUtil.writeToFile(usersFile, users);
                        log.debug("Отслежена активность пользователя {}: {}", userId, now);
                    } catch (IOException e) {
                        log.error("Ошибка при сохранении активности", e);
                    }
                });
        } catch (IOException e) {
            log.error("Ошибка при отслеживании активности", e);
        }
    }
    
    /**
     * Увеличивает счетчик вопросов и отслеживает активность
     */
    public void incrementQuestionsCount(Long userId) {
        try {
            List<User> users = jsonFileUtil.readFromFile(usersFile, User.class);
            
            users.stream()
                .filter(u -> u.getUserId().equals(userId))
                .findFirst()
                .ifPresent(user -> {
                    user.setQuestionsCount(user.getQuestionsCount() + 1);
                    LocalDateTime now = LocalDateTime.now();
                    updateUserActivity(user, now);
                    
                    try {
                        jsonFileUtil.writeToFile(usersFile, users);
                        log.debug("Увеличен счетчик вопросов для пользователя {}: {}", userId, user.getQuestionsCount());
                    } catch (IOException e) {
                        log.error("Ошибка при сохранении счетчика вопросов", e);
                    }
                });
        } catch (IOException e) {
            log.error("Ошибка при увеличении счетчика вопросов", e);
        }
    }
    
    /**
     * Обновляет активность пользователя (определяет возвращения)
     */
    private void updateUserActivity(User user, LocalDateTime now) {
        // Устанавливаем первую активность, если её еще нет
        if (user.getFirstActivityAt() == null) {
            user.setFirstActivityAt(now);
        }
        
        // Проверяем, является ли это возвращением (последняя активность была более 30 минут назад)
        LocalDateTime lastActivity = user.getLastActivityAt();
        if (lastActivity != null) {
            long minutesSinceLastActivity = java.time.Duration.between(lastActivity, now).toMinutes();
            
            // Если прошло более 30 минут - это новая сессия
            if (minutesSinceLastActivity > 30) {
                user.setSessionsCount(user.getSessionsCount() + 1);
                user.setIsReturningUser(true);
                log.debug("Обнаружена новая сессия для пользователя {} (прошло {} минут)", 
                    user.getUserId(), minutesSinceLastActivity);
            }
        } else {
            // Первая активность
            user.setSessionsCount(1);
        }
        
        // Обновляем последнюю активность
        user.setLastActivityAt(now);
    }
    
    public Optional<User> getUser(Long userId) {
        try {
            List<User> users = jsonFileUtil.readFromFile(usersFile, User.class);
            return users.stream()
                .filter(u -> u.getUserId().equals(userId))
                .findFirst();
        } catch (IOException e) {
            log.error("Ошибка при получении пользователя", e);
            return Optional.empty();
        }
    }
    
    /**
     * Получает всех пользователей
     */
    public List<User> getAllUsers() {
        try {
            return jsonFileUtil.readFromFile(usersFile, User.class);
        } catch (IOException e) {
            log.error("Ошибка при получении всех пользователей", e);
            return new ArrayList<>();
        }
    }
}

