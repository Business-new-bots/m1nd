package com.example.m1nd.service;

import com.example.m1nd.bot.M1ndTelegramBot;
import com.example.m1nd.model.HabitDailyTask;
import com.example.m1nd.repository.HabitDailyTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HabitEveningReminderService {
    private final HabitDailyTaskRepository habitDailyTaskRepository;
    private final UserService userService;
    private final I18nService i18nService;
    private final M1ndTelegramBot telegramBot;

    @Value("${app.habits.evening-reminder.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${app.habits.evening-reminder.cron:0 0 20 * * ?}")
    @Transactional
    public void sendEveningHabitReminders() {
        if (!enabled) {
            log.debug("Вечерние напоминания по привычкам отключены");
            return;
        }

        LocalDate today = LocalDate.now();
        List<HabitDailyTask> tasks = habitDailyTaskRepository
            .findByTaskDateAndRemindEveningTrueAndEveningReminderSentAtIsNull(today);
        if (tasks.isEmpty()) {
            log.debug("Нет задач для вечернего напоминания на {}", today);
            return;
        }

        int sent = 0;
        for (HabitDailyTask task : tasks) {
            try {
                String languageCode = userService.resolveLanguage(task.getUserId(), null);
                String message = i18nService.get(languageCode, "habits.evening_reminder.text", task.getTaskText());
                telegramBot.sendReminderMessage(task.getUserId(), message);

                task.setEveningReminderSentAt(LocalDateTime.now());
                habitDailyTaskRepository.save(task);
                sent++;
            } catch (Exception ex) {
                log.error("Не удалось отправить вечернее напоминание userId={}", task.getUserId(), ex);
            }
        }

        log.info("Отправлено {} вечерних напоминаний по привычкам", sent);
    }
}
