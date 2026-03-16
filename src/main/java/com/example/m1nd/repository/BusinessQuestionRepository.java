package com.example.m1nd.repository;

import com.example.m1nd.model.BusinessQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BusinessQuestionRepository extends JpaRepository<BusinessQuestion, Long> {

    Optional<BusinessQuestion> findById(Long id);
}

