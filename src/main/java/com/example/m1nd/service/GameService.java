package com.example.m1nd.service;

import com.example.m1nd.model.Game;
import com.example.m1nd.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRepository gameRepository;

    public List<Game> findAll() {
        return gameRepository.findAll();
    }

    public Optional<Game> findByCode(String code) {
        return gameRepository.findByCode(code);
    }

    public Game createFromTitle(String title, Long createdByUserId) {
        String baseCode = title.toLowerCase()
            .replaceAll("[^a-zа-я0-9]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "");

        if (baseCode.isBlank()) {
            baseCode = "game";
        }

        String code = baseCode;
        int suffix = 1;
        while (gameRepository.findByCode(code).isPresent()) {
            code = baseCode + "_" + suffix;
            suffix++;
        }

        Game game = new Game();
        game.setCode(code);
        game.setTitle(title);
        game.setPrompt("Дай контент для игры на тему: «" + title + "». Кратко.");

        return gameRepository.save(game);
    }

    public void deleteById(Long id) {
        gameRepository.deleteById(id);
    }
}
