package com.example.m1nd.model;

/**
 * Результат оценки ответа пользователя
 */
public class AnswerEvaluation {
    private final int score;
    private final String feedback;
    
    public AnswerEvaluation(int score, String feedback) {
        this.score = score;
        this.feedback = feedback;
    }
    
    public int getScore() {
        return score;
    }
    
    public String getFeedback() {
        return feedback;
    }
    
    public boolean hasFeedback() {
        return feedback != null && !feedback.trim().isEmpty();
    }
}

