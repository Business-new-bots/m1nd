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
    
    private static final String REMINDER_MESSAGE_TEMPLATE =
        "Эй, %s! Давно не виделись. Я рядом, чтобы помочь тебе задизайнить твою жизнь так, чтобы ты "
            + "по-настоящему наслаждался этим удивительным приключением.\n\n"
            + "Вместе мы можем внедрить привычки, которые приведут тебя к лучшей версии жизни — "
            + "наполненной смыслом, радостью и живыми моментами. Я здесь, чтобы поддержать тебя в "
            + "создании состояния, в котором ты чувствуешь себя счастливым, свободным и по-настоящему живым.";
    
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
     * Получает персональное сообщение для напоминания
     */
    private String getRandomReminderMessage(String firstName) {
        String displayName = (firstName != null && !firstName.isBlank()) ? firstName : "друг";
        return String.format(REMINDER_MESSAGE_TEMPLATE, displayName);
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
