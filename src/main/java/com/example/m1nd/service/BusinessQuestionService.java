package com.example.m1nd.service;

import com.example.m1nd.model.BusinessQuestion;
import com.example.m1nd.repository.BusinessQuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessQuestionService {

    private final BusinessQuestionRepository businessQuestionRepository;

    @Transactional
    public BusinessQuestion createQuestion(Long userId, Long assistantUserId, String questionText) {
        BusinessQuestion q = new BusinessQuestion();
        q.setUserId(userId);
        q.setAssistantUserId(assistantUserId);
        q.setQuestionText(questionText);
        q.setStatus("NEW");
        q.setCreatedAt(LocalDateTime.now());
        return businessQuestionRepository.save(q);
    }

    public Optional<BusinessQuestion> findById(Long id) {
        return businessQuestionRepository.findById(id);
    }

    @Transactional
    public Optional<BusinessQuestion> saveAnswer(Long questionId, String answerText) {
        Optional<BusinessQuestion> opt = businessQuestionRepository.findById(questionId);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        BusinessQuestion q = opt.get();
        q.setAnswerText(answerText);
        q.setStatus("ANSWERED");
        q.setAnsweredAt(LocalDateTime.now());
        return Optional.of(businessQuestionRepository.save(q));
    }
}

