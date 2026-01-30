package com.example.m1nd.service;

import com.example.m1nd.bot.M1ndTelegramBot;
import com.example.m1nd.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {
    
    private final UserService userService;
    private final M1ndTelegramBot telegramBot;
    
    @Value("${app.reminder.inactive-days:5}")
    private int inactiveDays;
    
    @Value("${app.reminder.enabled:true}")
    private boolean reminderEnabled;
    
    // Красивые сообщения для напоминаний
    private static final String[] REMINDER_MESSAGES = {
        "✨ Привет! Давно не виделись. У меня есть новые идеи и ответы, которые могут быть полезны. Задай вопрос, и я помогу!",
        "🌟 Эй, давно не общались! Я здесь, чтобы помочь тебе найти ответы и ресурсы. Что тебя интересует?",
        "💫 Помнишь меня? Я твой помощник в поиске знаний и ответов. Готов помочь прямо сейчас — просто спроси!",
        "🎯 Привет! Я заметил, что мы давно не общались. У меня есть время и готовность помочь. О чем хочешь узнать?",
        "🚀 Эй! Давно не виделись. Я здесь, чтобы поддержать твой рост и развитие. Задай вопрос, и начнем!",
        "💡 Привет! Я заметил, что ты давно не обращался. Я готов помочь с любыми вопросами — просто напиши!",
        "🌱 Эй! Давно не общались. Я здесь, чтобы помочь тебе найти ресурсы для роста и развития. Что тебя интересует?",
        "🎨 Привет! Я скучаю по нашим беседам. Готов помочь с ответами и поддержкой. Задай вопрос!",
        "⚡ Эй! Давно не виделись. Я здесь, чтобы помочь тебе найти знания и ответы. Что хочешь узнать?",
        "🌈 Привет! Я заметил, что мы давно не общались. Готов помочь с любыми вопросами — просто спроси!"
    };
    
    /**
     * Проверяет неактивных пользователей и отправляет напоминания
     * Запускается каждый день в 10:00 по времени сервера
     */
    @Scheduled(cron = "${app.reminder.cron:0 0 10 * * ?}")
    public void sendRemindersToInactiveUsers() {
        if (!reminderEnabled) {
            log.debug("Напоминания отключены");
            return;
        }
        
        log.info("Запуск проверки неактивных пользователей для отправки напоминаний");
        
        try {
            List<User> allUsers = userService.getAllUsers();
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime cutoffDate = now.minus(inactiveDays, ChronoUnit.DAYS);
            
            int remindersSent = 0;
            
            for (User user : allUsers) {
                // Пропускаем пользователей без lastActivityAt
                if (user.getLastActivityAt() == null) {
                    continue;
                }
                
                // Проверяем, что пользователь неактивен более N дней
                if (user.getLastActivityAt().isBefore(cutoffDate)) {
                    // Проверяем, что напоминание не отправлялось недавно (не чаще раза в 3 дня)
                    LocalDateTime lastReminder = user.getLastReminderSentAt();
                    if (lastReminder == null || 
                        lastReminder.isBefore(now.minus(3, ChronoUnit.DAYS))) {
                        
                        // Отправляем напоминание
                        if (sendReminder(user)) {
                            // Обновляем дату последнего напоминания
                            updateLastReminderSentAt(user, now);
                            remindersSent++;
                            
                            // Небольшая задержка между отправками, чтобы не перегружать API
                            try {
                                Thread.sleep(1000); // 1 секунда между сообщениями
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                log.warn("Прервана отправка напоминаний");
                                break;
                            }
                        }
                    }
                }
            }
            
            log.info("Отправлено {} напоминаний неактивным пользователям", remindersSent);
            
        } catch (Exception e) {
            log.error("Ошибка при отправке напоминаний", e);
        }
    }
    
    /**
     * Отправляет напоминание пользователю
     */
    private boolean sendReminder(User user) {
        try {
            Long userId = user.getUserId();
            String firstName = user.getFirstName() != null ? user.getFirstName() : "друг";
            
            // Выбираем случайное сообщение
            String message = getRandomReminderMessage(firstName);
            
            // Отправляем через бота
            telegramBot.sendReminderMessage(userId, message);
            
            log.info("Напоминание отправлено пользователю {} (userId: {})", 
                user.getUsername() != null ? user.getUsername() : firstName, userId);
            
            return true;
        } catch (Exception e) {
            log.error("Ошибка при отправке напоминания пользователю {}", user.getUserId(), e);
            return false;
        }
    }
    
    /**
     * Получает случайное сообщение для напоминания
     */
    private String getRandomReminderMessage(String firstName) {
        Random random = new Random();
        String baseMessage = REMINDER_MESSAGES[random.nextInt(REMINDER_MESSAGES.length)];
        
        // Персонализируем сообщение, если есть имя
        if (firstName != null && !firstName.isEmpty()) {
            return baseMessage.replace("Привет!", "Привет, " + firstName + "!")
                             .replace("Эй!", "Эй, " + firstName + "!");
        }
        
        return baseMessage;
    }
    
    /**
     * Обновляет дату последнего отправленного напоминания
     */
    private void updateLastReminderSentAt(User user, LocalDateTime now) {
        try {
            user.setLastReminderSentAt(now);
            // Обновляем пользователя через UserService
            userService.updateUser(user);
            log.debug("Обновлена дата последнего напоминания для пользователя {}", user.getUserId());
        } catch (Exception e) {
            log.error("Ошибка при обновлении даты последнего напоминания", e);
        }
    }
}
