package com.example.m1nd.repository;

import com.example.m1nd.model.IdeaTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdeaTopicRepository extends JpaRepository<IdeaTopic, Long> {

    Optional<IdeaTopic> findByCode(String code);
}
