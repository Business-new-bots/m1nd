package com.example.m1nd.repository;

import com.example.m1nd.model.HabitPatternMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HabitPatternMessageRepository extends JpaRepository<HabitPatternMessage, Long> {
    List<HabitPatternMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
}
