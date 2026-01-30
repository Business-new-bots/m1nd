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
@Table(name = "feedbacks")
public class Feedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    
    @Column(name = "feedback_id", unique = true)
    @JsonProperty("id")
    private String feedbackId;  // UUID для уникальности (из JSON)
    
    @Column(name = "user_id", nullable = false)
    @JsonProperty("userId")
    private Long userId;
    
    @Column(name = "username")
    @JsonProperty("username")
    private String username;
    
    @Column(name = "first_name")
    @JsonProperty("firstName")
    private String firstName;
    
    @Column(name = "rating")
    @JsonProperty("rating")
    private Integer rating;  // 1-5 или 1/0 (понравилось/не понравилось)
    
    @Column(name = "was_useful")
    @JsonProperty("wasUseful")
    private Boolean wasUseful;  // Принесло ли пользу
    
    @Column(name = "comment", columnDefinition = "TEXT")
    @JsonProperty("comment")
    private String comment;  // Комментарий пользователя
    
    @Column(name = "question", columnDefinition = "TEXT")
    @JsonProperty("question")
    private String question;  // Вопрос, на который был дан ответ
    
    @Column(name = "created_at", nullable = false)
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
}
