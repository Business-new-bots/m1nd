package com.example.m1nd.repository;

import com.example.m1nd.model.HabitTrackerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HabitTrackerEntryRepository extends JpaRepository<HabitTrackerEntry, Long> {
    List<HabitTrackerEntry> findByUserIdOrderByCreatedAtDesc(Long userId);
}
