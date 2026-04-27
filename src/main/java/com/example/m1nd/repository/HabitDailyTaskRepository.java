package com.example.m1nd.repository;

import com.example.m1nd.model.HabitDailyTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HabitDailyTaskRepository extends JpaRepository<HabitDailyTask, Long> {
    @Modifying
    @Transactional
    void deleteByTaskDateBefore(LocalDate date);

    @Modifying
    @Transactional
    void deleteByUserIdAndTaskDate(Long userId, LocalDate taskDate);

    Optional<HabitDailyTask> findTopByUserIdAndTaskDateOrderByCreatedAtDesc(Long userId, LocalDate taskDate);
    List<HabitDailyTask> findByTaskDateAndRemindEveningTrueAndEveningReminderSentAtIsNull(LocalDate taskDate);
}
