package com.example.m1nd.service;

import com.example.m1nd.model.UserProgress;
import com.example.m1nd.repository.UserProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProgressService {

    private final UserProgressRepository userProgressRepository;

    @Transactional
    public UserProgress getOrCreate(Long userId) {
        return userProgressRepository.findById(userId)
            .orElseGet(() -> {
                UserProgress p = new UserProgress();
                p.setUserId(userId);
                p.setRiddlesSolved(0);
                p.setTasksCompleted(0);
                p.setFactsViewed(0);
                p.setIdeasViewed(0);
                p.setMotivationsViewed(0);
                p.setGamesPlayed(0);
                return userProgressRepository.save(p);
            });
    }

    @Transactional
    public void incrementRiddlesSolved(Long userId) {
        UserProgress p = getOrCreate(userId);
        p.setRiddlesSolved(p.getRiddlesSolved() + 1);
        userProgressRepository.save(p);
    }

    @Transactional
    public void incrementTasksCompleted(Long userId) {
        UserProgress p = getOrCreate(userId);
        p.setTasksCompleted(p.getTasksCompleted() + 1);
        userProgressRepository.save(p);
    }

    @Transactional
    public void incrementFactsViewed(Long userId) {
        UserProgress p = getOrCreate(userId);
        p.setFactsViewed(p.getFactsViewed() + 1);
        userProgressRepository.save(p);
    }

    @Transactional
    public void incrementIdeasViewed(Long userId) {
        UserProgress p = getOrCreate(userId);
        p.setIdeasViewed(p.getIdeasViewed() + 1);
        userProgressRepository.save(p);
    }

    @Transactional
    public void incrementMotivationsViewed(Long userId) {
        UserProgress p = getOrCreate(userId);
        p.setMotivationsViewed(p.getMotivationsViewed() + 1);
        userProgressRepository.save(p);
    }

    @Transactional
    public void incrementGamesPlayed(Long userId) {
        UserProgress p = getOrCreate(userId);
        p.setGamesPlayed(p.getGamesPlayed() + 1);
        userProgressRepository.save(p);
    }

    public int getRiddlesSolved(Long userId) {
        return getOrCreate(userId).getRiddlesSolved();
    }

    public int getTasksCompleted(Long userId) {
        return getOrCreate(userId).getTasksCompleted();
    }

    public int getFactsViewed(Long userId) {
        return getOrCreate(userId).getFactsViewed();
    }

    public int getIdeasViewed(Long userId) {
        return getOrCreate(userId).getIdeasViewed();
    }

    public int getMotivationsViewed(Long userId) {
        return getOrCreate(userId).getMotivationsViewed();
    }

    public int getGamesPlayed(Long userId) {
        return getOrCreate(userId).getGamesPlayed();
    }
}
