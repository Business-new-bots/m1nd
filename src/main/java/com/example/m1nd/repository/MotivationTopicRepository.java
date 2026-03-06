package com.example.m1nd.repository;

import com.example.m1nd.model.MotivationTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MotivationTopicRepository extends JpaRepository<MotivationTopic, Long> {

    Optional<MotivationTopic> findByCode(String code);
}
