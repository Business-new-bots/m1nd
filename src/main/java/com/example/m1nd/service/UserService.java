package com.example.m1nd.service;

import com.example.m1nd.model.User;
import com.example.m1nd.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.Update;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository userRepository;
    
    @PostConstruct
    public void init() {
        log.info("UserService инициализирован. Используется БД (PostgreSQL)");
        long count = userRepository.count();
        log.info("В БД загружено {} пользователей", count);
    }
    
    @Transactional
    public void registerUser(Update update) {
        try {
            Long userId = update.getMessage().getFrom().getId();
            String username = update.getMessage().getFrom().getUserName();
            String firstName = update.getMessage().getFrom().getFirstName();
            String lastName = update.getMessage().getFrom().getLastName();
            
            LocalDateTime now = LocalDateTime.now();
            
            Optional<User> existingUserOpt = userRepository.findByUserId(userId);
            
            if (existingUserOpt.isEmpty()) {
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
                
                userRepository.save(newUser);
                log.info("Зарегистрирован новый пользователь в БД: {}", userId);
            } else {
                // Обновляем существующего пользователя
                User user = existingUserOpt.get();
                updateUserActivity(user, now);
                
                // Обновляем данные пользователя, если они изменились
                user.setUsername(username);
                user.setFirstName(firstName);
                user.setLastName(lastName);
                
                userRepository.save(user);
                log.debug("Обновлена активность пользователя в БД {}: {}", userId, now);
            }
        } catch (Exception e) {
            log.error("Ошибка при регистрации пользователя в БД", e);
        }
    }
    
    /**
     * Отслеживает активность пользователя (любое взаимодействие)
     */
    @Transactional
    public void trackUserActivity(Long userId) {
        try {
            Optional<User> userOpt = userRepository.findByUserId(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                LocalDateTime now = LocalDateTime.now();
                updateUserActivity(user, now);
                
                // Увеличиваем счетчик сообщений
                user.setTotalMessages(user.getTotalMessages() + 1);
                
                userRepository.save(user);
                log.debug("Отслежена активность пользователя в БД {}: {}", userId, now);
            }
        } catch (Exception e) {
            log.error("Ошибка при отслеживании активности в БД", e);
        }
    }
    
    /**
     * Увеличивает счетчик вопросов и отслеживает активность
     */
    @Transactional
    public void incrementQuestionsCount(Long userId) {
        try {
            Optional<User> userOpt = userRepository.findByUserId(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                user.setQuestionsCount(user.getQuestionsCount() + 1);
                LocalDateTime now = LocalDateTime.now();
                updateUserActivity(user, now);
                
                userRepository.save(user);
                log.debug("Увеличен счетчик вопросов в БД для пользователя {}: {}", userId, user.getQuestionsCount());
            }
        } catch (Exception e) {
            log.error("Ошибка при увеличении счетчика вопросов в БД", e);
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
        return userRepository.findByUserId(userId);
    }
    
    /**
     * Получает всех пользователей
     */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
    
    /**
     * Обновляет данные пользователя
     */
    @Transactional
    public void updateUser(User user) {
        try {
            Optional<User> existingUserOpt = userRepository.findByUserId(user.getUserId());
            if (existingUserOpt.isPresent()) {
                User existingUser = existingUserOpt.get();
                // Обновляем все поля
                existingUser.setUsername(user.getUsername());
                existingUser.setFirstName(user.getFirstName());
                existingUser.setLastName(user.getLastName());
                existingUser.setRegisteredAt(user.getRegisteredAt());
                existingUser.setFirstActivityAt(user.getFirstActivityAt());
                existingUser.setLastActivityAt(user.getLastActivityAt());
                existingUser.setQuestionsCount(user.getQuestionsCount());
                existingUser.setTotalMessages(user.getTotalMessages());
                existingUser.setSessionsCount(user.getSessionsCount());
                existingUser.setIsReturningUser(user.getIsReturningUser());
                existingUser.setLastReminderSentAt(user.getLastReminderSentAt());
                
                userRepository.save(existingUser);
                log.debug("Обновлен пользователь в БД {}", user.getUserId());
            } else {
                // Если пользователя нет, создаем нового
                userRepository.save(user);
                log.debug("Создан новый пользователь в БД {}", user.getUserId());
            }
        } catch (Exception e) {
            log.error("Ошибка при обновлении пользователя в БД", e);
        }
    }
}
