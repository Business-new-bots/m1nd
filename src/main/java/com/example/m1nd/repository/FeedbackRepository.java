package com.example.m1nd.repository;

import com.example.m1nd.model.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime date);
    Optional<Feedback> findByFeedbackId(String feedbackId);
}
