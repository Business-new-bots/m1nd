package com.example.m1nd.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @JsonProperty("userId")
    private Long userId;
    
    @JsonProperty("username")
    private String username;
    
    @JsonProperty("firstName")
    private String firstName;
    
    @JsonProperty("lastName")
    private String lastName;
    
    @JsonProperty("registeredAt")
    private LocalDateTime registeredAt;
    
    @JsonProperty("firstActivityAt")
    private LocalDateTime firstActivityAt;  // Первая активность (может отличаться от регистрации)
    
    @JsonProperty("lastActivityAt")
    private LocalDateTime lastActivityAt;  // Последняя активность
    
    @JsonProperty("questionsCount")
    private Integer questionsCount = 0;
    
    @JsonProperty("totalMessages")
    private Integer totalMessages = 0;  // Всего сообщений (включая команды)
    
    @JsonProperty("sessionsCount")
    private Integer sessionsCount = 0;  // Количество сессий (возвращений)
    
    @JsonProperty("isReturningUser")
    private Boolean isReturningUser = false;  // Является ли возвращающимся пользователем
}

