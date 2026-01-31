package com.example.m1nd.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User {
    @Id
    @Column(name = "user_id")
    @JsonProperty("userId")
    private Long userId;
    
    @Column(name = "username")
    @JsonProperty("username")
    private String username;
    
    @Column(name = "first_name")
    @JsonProperty("firstName")
    private String firstName;
    
    @Column(name = "last_name")
    @JsonProperty("lastName")
    private String lastName;
    
    @Column(name = "registered_at")
    @JsonProperty("registeredAt")
    private LocalDateTime registeredAt;
    
    @Column(name = "first_activity_at")
    @JsonProperty("firstActivityAt")
    private LocalDateTime firstActivityAt;  // Первая активность (может отличаться от регистрации)
    
    @Column(name = "last_activity_at")
    @JsonProperty("lastActivityAt")
    private LocalDateTime lastActivityAt;  // Последняя активность
    
    @Column(name = "questions_count")
    @JsonProperty("questionsCount")
    private Integer questionsCount = 0;
    
    @Column(name = "total_messages")
    @JsonProperty("totalMessages")
    private Integer totalMessages = 0;  // Всего сообщений (включая команды)
    
    @Column(name = "sessions_count")
    @JsonProperty("sessionsCount")
    private Integer sessionsCount = 0;  // Количество сессий (возвращений)
    
    @Column(name = "is_returning_user")
    @JsonProperty("isReturningUser")
    private Boolean isReturningUser = false;  // Является ли возвращающимся пользователем
    
    @Column(name = "last_reminder_sent_at")
    @JsonProperty("lastReminderSentAt")
    private LocalDateTime lastReminderSentAt;  // Дата последнего отправленного напоминания
}

