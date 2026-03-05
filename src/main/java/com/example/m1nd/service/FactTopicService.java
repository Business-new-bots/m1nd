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

    public FactTopic createFromTitle(String title, Long createdByUserId) {
        String baseCode = title.toLowerCase()
            .replace("факты", "")
            .replace("о", "")
            .replaceAll("[^a-zа-я0-9]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "");

        if (baseCode.isBlank()) {
            baseCode = "topic";
        }

        String code = baseCode;
        int suffix = 1;
        while (factTopicRepository.findByCode(code).isPresent()) {
            code = baseCode + "_" + suffix;
            suffix++;
        }

        String prompt = "Дай один интересный и достоверный факт на тему: «"
            + title + "». Кратко: 1–3 предложения.";

        FactTopic topic = new FactTopic();
        topic.setCode(code);
        topic.setTitle(title);
        topic.setPrompt(prompt);

        return factTopicRepository.save(topic);
    }

    public void deleteById(Long id) {
        factTopicRepository.deleteById(id);
    }
}

