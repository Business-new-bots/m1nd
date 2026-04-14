package com.example.m1nd.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "habit_daily_tasks")
public class HabitDailyTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "habit_tracker_entry_id")
    private Long habitTrackerEntryId;

    @Column(name = "task_text", nullable = false, columnDefinition = "TEXT")
    private String taskText;

    @Column(name = "task_date", nullable = false)
    private LocalDate taskDate;

    @Column(name = "remind_evening", nullable = false)
    private Boolean remindEvening;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
