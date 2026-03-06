package com.example.m1nd.service;

import com.example.m1nd.model.MotivationTopic;
import com.example.m1nd.repository.MotivationTopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MotivationTopicService {

    private final MotivationTopicRepository motivationTopicRepository;

    public List<MotivationTopic> findAll() {
        return motivationTopicRepository.findAll();
    }

    public Optional<MotivationTopic> findByCode(String code) {
        return motivationTopicRepository.findByCode(code);
    }

    public MotivationTopic createFromTitle(String title, Long createdByUserId) {
        String baseCode = title.toLowerCase()
            .replaceAll("[^a-zа-я0-9]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "");

        if (baseCode.isBlank()) {
            baseCode = "motivation";
        }

        String code = baseCode;
        int suffix = 1;
        while (motivationTopicRepository.findByCode(code).isPresent()) {
            code = baseCode + "_" + suffix;
            suffix++;
        }

        String prompt = "Дай один мотивирующий контент на тему: «" + title + "». Кратко: 1–3 предложения.";

        MotivationTopic topic = new MotivationTopic();
        topic.setCode(code);
        topic.setTitle(title);
        topic.setPrompt(prompt);

        return motivationTopicRepository.save(topic);
    }

    public void deleteById(Long id) {
        motivationTopicRepository.deleteById(id);
    }
}
