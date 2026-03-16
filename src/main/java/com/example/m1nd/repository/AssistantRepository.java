package com.example.m1nd.repository;

import com.example.m1nd.model.Assistant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssistantRepository extends JpaRepository<Assistant, Long> {

    boolean existsByTelegramUserIdAndActiveTrue(Long telegramUserId);

    Optional<Assistant> findByTelegramUserIdAndActiveTrue(Long telegramUserId);

    List<Assistant> findByActiveTrue();
}

