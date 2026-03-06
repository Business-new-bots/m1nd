package com.example.m1nd.service;

import com.example.m1nd.model.IdeaTopic;
import com.example.m1nd.repository.IdeaTopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdeaTopicService {

    private final IdeaTopicRepository ideaTopicRepository;

    public List<IdeaTopic> findAll() {
        return ideaTopicRepository.findAll();
    }

    public Optional<IdeaTopic> findByCode(String code) {
        return ideaTopicRepository.findByCode(code);
    }

    public IdeaTopic createFromTitle(String title, Long createdByUserId) {
        String baseCode = title.toLowerCase()
            .replace("идеи", "")
            .replaceAll("[^a-zа-я0-9]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "");

        if (baseCode.isBlank()) {
            baseCode = "idea";
        }

        String code = baseCode;
        int suffix = 1;
        while (ideaTopicRepository.findByCode(code).isPresent()) {
            code = baseCode + "_" + suffix;
            suffix++;
        }

        String prompt = "Дай одну идею или инсайт на тему: «" + title + "». Кратко: 1–3 предложения.";

        IdeaTopic topic = new IdeaTopic();
        topic.setCode(code);
        topic.setTitle(title);
        topic.setPrompt(prompt);

        return ideaTopicRepository.save(topic);
    }

    public void deleteById(Long id) {
        ideaTopicRepository.deleteById(id);
    }
}
