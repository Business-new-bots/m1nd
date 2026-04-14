package com.example.m1nd.repository;

import com.example.m1nd.model.HabitDailyTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface HabitDailyTaskRepository extends JpaRepository<HabitDailyTask, Long> {
    void deleteByTaskDateBefore(LocalDate date);
    void deleteByUserIdAndTaskDate(Long userId, LocalDate taskDate);
    Optional<HabitDailyTask> findTopByUserIdAndTaskDateOrderByCreatedAtDesc(Long userId, LocalDate taskDate);
}
