package com.example.m1nd.service;

import com.example.m1nd.model.FactTopic;
import com.example.m1nd.repository.FactTopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FactTopicService {

    private final FactTopicRepository factTopicRepository;

    public List<FactTopic> findAll() {
        return factTopicRepository.findAll();
    }

    public Optional<FactTopic> findByCode(String code) {
        return factTopicRepository.findByCode(code);
    }

    // Методы для будущего редактора (добавление/удаление тем) можно будет добавить сюда позже
}

