package com.example.m1nd.service;

import com.example.m1nd.model.Feedback;
import com.example.m1nd.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {
    
    private final FeedbackRepository feedbackRepository;
    
    @PostConstruct
    public void init() {
        log.info("FeedbackService инициализирован. Используется БД (PostgreSQL)");
        long count = feedbackRepository.count();
        log.info("В БД загружено {} опросов", count);
    }
    
    /**
     * Сохраняет опрос от пользователя
     */
    @Transactional
    public void saveFeedback(Long userId, String username, String firstName, 
                             Integer rating, Boolean wasUseful, String comment, String question) {
        try {
            Feedback feedback = new Feedback();
            feedback.setFeedbackId(UUID.randomUUID().toString());
            feedback.setUserId(userId);
            feedback.setUsername(username);
            feedback.setFirstName(firstName);
            feedback.setRating(rating);
            feedback.setWasUseful(wasUseful);
            feedback.setComment(comment);
            feedback.setQuestion(question);
            feedback.setCreatedAt(LocalDateTime.now());
            
            feedbackRepository.save(feedback);
            
            log.info("Сохранен опрос в БД от пользователя {} (userId: {})", username, userId);
        } catch (Exception e) {
            log.error("Ошибка при сохранении опроса в БД", e);
        }
    }
    
    /**
     * Получает все опросы
     */
    public List<Feedback> getAllFeedbacks() {
        return feedbackRepository.findAll();
    }
    
    /**
     * Получает опросы за последние N дней
     */
    public List<Feedback> getRecentFeedbacks(int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return feedbackRepository.findByCreatedAtAfterOrderByCreatedAtDesc(cutoff);
    }
}
