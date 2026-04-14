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

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "habit_tracker_entries")
public class HabitTrackerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "habit_area", nullable = false)
    private String habitArea;

    @Column(name = "habit_name", nullable = false, columnDefinition = "TEXT")
    private String habitName;

    @Column(name = "duration_plan", nullable = false)
    private String durationPlan;

    @Column(name = "frequency_plan", nullable = false)
    private String frequencyPlan;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
