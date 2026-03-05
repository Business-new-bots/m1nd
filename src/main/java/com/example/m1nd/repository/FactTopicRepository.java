package com.example.m1nd.repository;

import com.example.m1nd.model.FactTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FactTopicRepository extends JpaRepository<FactTopic, Long> {

    Optional<FactTopic> findByCode(String code);
}

