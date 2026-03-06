package com.example.m1nd.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_progress")
public class UserProgress {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "riddles_solved", nullable = false)
    private int riddlesSolved = 0;

    @Column(name = "tasks_completed", nullable = false)
    private int tasksCompleted = 0;
}
