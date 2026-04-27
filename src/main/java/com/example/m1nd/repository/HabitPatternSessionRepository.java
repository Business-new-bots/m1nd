package com.example.m1nd.repository;

import com.example.m1nd.model.HabitPatternSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HabitPatternSessionRepository extends JpaRepository<HabitPatternSession, Long> {
    Optional<HabitPatternSession> findTopByUserIdAndStatusOrderByUpdatedAtDesc(Long userId, String status);
    Optional<HabitPatternSession> findTopByUserIdOrderByUpdatedAtDesc(Long userId);
}
