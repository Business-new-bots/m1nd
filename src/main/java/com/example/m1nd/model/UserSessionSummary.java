package com.example.m1nd.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_session_summaries")
public class UserSessionSummary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "username")
    private String username;

    @Column(name = "summary_question", columnDefinition = "TEXT", nullable = false)
    private String summaryQuestion;

    @Column(name = "summary_answer", columnDefinition = "TEXT", nullable = false)
    private String summaryAnswer;

    @Column(name = "session_start_at")
    private LocalDateTime sessionStartAt;

    @Column(name = "session_end_at")
    private LocalDateTime sessionEndAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
