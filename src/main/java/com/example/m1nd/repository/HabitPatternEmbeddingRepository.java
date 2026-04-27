package com.example.m1nd.repository;

import com.example.m1nd.model.HabitPatternEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HabitPatternEmbeddingRepository extends JpaRepository<HabitPatternEmbedding, Long> {
    List<HabitPatternEmbedding> findTop100ByUserIdOrderByCreatedAtDesc(Long userId);
}
