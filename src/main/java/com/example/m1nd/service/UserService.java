package com.example.m1nd.service;

import com.example.m1nd.model.User;
import com.example.m1nd.util.JsonFileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;

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
            
            if (existingUser.isEmpty()) {
                // Создаем нового пользователя
                User newUser = new User();
                newUser.setUserId(userId);
                newUser.setUsername(username);
                newUser.setFirstName(firstName);
                newUser.setLastName(lastName);
                newUser.setRegisteredAt(LocalDateTime.now());
                newUser.setQuestionsCount(0);
                
                users.add(newUser);
                jsonFileUtil.writeToFile(usersFile, users);
                log.info("Зарегистрирован новый пользователь: {}", userId);
            } else {
                log.debug("Пользователь {} уже зарегистрирован", userId);
            }
        } catch (IOException e) {
            log.error("Ошибка при регистрации пользователя", e);
        }
    }
    
    public void incrementQuestionsCount(Long userId) {
        try {
            List<User> users = jsonFileUtil.readFromFile(usersFile, User.class);
            
            users.stream()
                .filter(u -> u.getUserId().equals(userId))
                .findFirst()
                .ifPresent(user -> {
                    user.setQuestionsCount(user.getQuestionsCount() + 1);
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
}

