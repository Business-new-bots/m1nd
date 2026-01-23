package com.example.m1nd.service;

import com.example.m1nd.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {
    
    private final UserService userService;
    
    /**
     * Общая статистика бота
     */
    public Statistics getOverallStatistics() {
        List<User> users = userService.getAllUsers();
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime weekStart = now.minus(7, ChronoUnit.DAYS);
        LocalDateTime monthStart = now.minus(30, ChronoUnit.DAYS);
        
        long totalUsers = users.size();
        long newUsersToday = users.stream()
            .filter(u -> u.getRegisteredAt() != null && u.getRegisteredAt().isAfter(todayStart))
            .count();
        
        long activeUsersToday = users.stream()
            .filter(u -> u.getLastActivityAt() != null && u.getLastActivityAt().isAfter(todayStart))
            .count();
        
        long activeUsersWeek = users.stream()
            .filter(u -> u.getLastActivityAt() != null && u.getLastActivityAt().isAfter(weekStart))
            .count();
        
        long activeUsersMonth = users.stream()
            .filter(u -> u.getLastActivityAt() != null && u.getLastActivityAt().isAfter(monthStart))
            .count();
        
        long returningUsers = users.stream()
            .filter(u -> Boolean.TRUE.equals(u.getIsReturningUser()))
            .count();
        
        int totalQuestions = users.stream()
            .mapToInt(u -> u.getQuestionsCount() != null ? u.getQuestionsCount() : 0)
            .sum();
        
        int totalMessages = users.stream()
            .mapToInt(u -> u.getTotalMessages() != null ? u.getTotalMessages() : 0)
            .sum();
        
        int totalSessions = users.stream()
            .mapToInt(u -> u.getSessionsCount() != null ? u.getSessionsCount() : 0)
            .sum();
        
        return Statistics.builder()
            .totalUsers(totalUsers)
            .newUsersToday(newUsersToday)
            .activeUsersToday(activeUsersToday)
            .activeUsersWeek(activeUsersWeek)
            .activeUsersMonth(activeUsersMonth)
            .returningUsers(returningUsers)
            .totalQuestions(totalQuestions)
            .totalMessages(totalMessages)
            .totalSessions(totalSessions)
            .build();
    }
    
    /**
     * Статистика по дням (последние N дней)
     */
    public Map<String, Long> getDailyStatistics(int days) {
        List<User> users = userService.getAllUsers();
        LocalDateTime now = LocalDateTime.now();
        
        return java.util.stream.IntStream.range(0, days)
            .boxed()
            .collect(Collectors.toMap(
                i -> now.minus(i, ChronoUnit.DAYS).toLocalDate().toString(),
                i -> {
                    LocalDateTime dayStart = now.minus(i, ChronoUnit.DAYS).toLocalDate().atStartOfDay();
                    LocalDateTime dayEnd = dayStart.plus(1, ChronoUnit.DAYS);
                    
                    return users.stream()
                        .filter(u -> u.getLastActivityAt() != null 
                            && u.getLastActivityAt().isAfter(dayStart)
                            && u.getLastActivityAt().isBefore(dayEnd))
                        .count();
                }
            ));
    }
    
    /**
     * Топ пользователей по активности
     */
    public List<User> getTopActiveUsers(int limit) {
        return userService.getAllUsers().stream()
            .sorted((u1, u2) -> {
                int messages1 = u1.getTotalMessages() != null ? u1.getTotalMessages() : 0;
                int messages2 = u2.getTotalMessages() != null ? u2.getTotalMessages() : 0;
                return Integer.compare(messages2, messages1);
            })
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    /**
     * Топ пользователей по количеству вопросов
     */
    public List<User> getTopUsersByQuestions(int limit) {
        return userService.getAllUsers().stream()
            .sorted((u1, u2) -> {
                int q1 = u1.getQuestionsCount() != null ? u1.getQuestionsCount() : 0;
                int q2 = u2.getQuestionsCount() != null ? u2.getQuestionsCount() : 0;
                return Integer.compare(q2, q1);
            })
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    /**
     * Форматирует статистику для вывода
     */
    public String formatStatistics() {
        Statistics stats = getOverallStatistics();
        
        StringBuilder sb = new StringBuilder();
        sb.append("📊 Статистика бота\n\n");
        sb.append("👥 Пользователи:\n");
        sb.append("  • Всего: ").append(stats.getTotalUsers()).append("\n");
        sb.append("  • Новых сегодня: ").append(stats.getNewUsersToday()).append("\n");
        sb.append("  • Активных сегодня (DAU): ").append(stats.getActiveUsersToday()).append("\n");
        sb.append("  • Активных за неделю (WAU): ").append(stats.getActiveUsersWeek()).append("\n");
        sb.append("  • Активных за месяц (MAU): ").append(stats.getActiveUsersMonth()).append("\n");
        sb.append("  • Возвращающихся: ").append(stats.getReturningUsers()).append("\n\n");
        
        sb.append("💬 Активность:\n");
        sb.append("  • Всего сообщений: ").append(stats.getTotalMessages()).append("\n");
        sb.append("  • Всего вопросов: ").append(stats.getTotalQuestions()).append("\n");
        sb.append("  • Всего сессий: ").append(stats.getTotalSessions()).append("\n");
        
        if (stats.getTotalUsers() > 0) {
            double avgMessagesPerUser = (double) stats.getTotalMessages() / stats.getTotalUsers();
            double avgQuestionsPerUser = (double) stats.getTotalQuestions() / stats.getTotalUsers();
            sb.append("\n📈 Средние показатели:\n");
            sb.append("  • Сообщений на пользователя: ").append(String.format("%.2f", avgMessagesPerUser)).append("\n");
            sb.append("  • Вопросов на пользователя: ").append(String.format("%.2f", avgQuestionsPerUser)).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * DTO для статистики
     */
    @lombok.Data
    @lombok.Builder
    public static class Statistics {
        private long totalUsers;
        private long newUsersToday;
        private long activeUsersToday;
        private long activeUsersWeek;
        private long activeUsersMonth;
        private long returningUsers;
        private int totalQuestions;
        private int totalMessages;
        private int totalSessions;
    }
}
