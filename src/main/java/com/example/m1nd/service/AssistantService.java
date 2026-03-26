package com.example.m1nd.service;

import com.example.m1nd.model.Assistant;
import com.example.m1nd.model.AssistantType;
import com.example.m1nd.model.User;
import com.example.m1nd.repository.AssistantRepository;
import com.example.m1nd.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantService {

    private final AssistantRepository assistantRepository;
    private final UserRepository userRepository;
    private final Random random = new Random();

    public boolean isAssistant(Long userId) {
        if (userId == null) {
            return false;
        }
        return assistantRepository.existsByTelegramUserIdAndActiveTrue(userId);
    }

    public List<Assistant> getActiveAssistants() {
        return assistantRepository.findByActiveTrue();
    }

    public List<Assistant> getActiveAssistantsByType(AssistantType type) {
        if (type == null) {
            return List.of();
        }
        return assistantRepository.findByActiveTrueAndType(type);
    }

    public Optional<Assistant> findRandomActiveAssistant() {
        List<Assistant> active = getActiveAssistants();
        if (active.isEmpty()) {
            return Optional.empty();
        }
        int index = random.nextInt(active.size());
        return Optional.of(active.get(index));
    }

    public Optional<Assistant> findRandomActiveAssistantByType(AssistantType type) {
        List<Assistant> active = getActiveAssistantsByType(type);
        if (active.isEmpty()) {
            return Optional.empty();
        }
        int index = random.nextInt(active.size());
        return Optional.of(active.get(index));
    }

    @Transactional
    public Optional<Assistant> addAssistantByUsername(String username, AssistantType type) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        if (type == null) {
            return Optional.empty();
        }

        String cleanUsername = username.startsWith("@") ? username.substring(1) : username;

        Optional<User> userOpt = userRepository.findAll().stream()
            .filter(u -> cleanUsername.equalsIgnoreCase(
                u.getUsername() != null && u.getUsername().startsWith("@")
                    ? u.getUsername().substring(1)
                    : u.getUsername()))
            .findFirst();

        if (userOpt.isEmpty()) {
            log.warn("Не найден пользователь с username {} в таблице users", cleanUsername);
            return Optional.empty();
        }

        User user = userOpt.get();

        Optional<Assistant> existing = assistantRepository.findByTelegramUserIdAndActiveTrue(user.getUserId());
        if (existing.isPresent()) {
            Assistant assistant = existing.get();
            assistant.setType(type);
            assistant.setUpdatedAt(LocalDateTime.now());
            return Optional.of(assistantRepository.save(assistant));
        }

        Assistant assistant = new Assistant();
        assistant.setTelegramUserId(user.getUserId());
        assistant.setUsername(user.getUsername());
        assistant.setFirstName(user.getFirstName());
        assistant.setType(type);
        assistant.setActive(true);
        assistant.setCreatedAt(LocalDateTime.now());
        assistant.setUpdatedAt(LocalDateTime.now());

        Assistant saved = assistantRepository.save(assistant);
        return Optional.of(saved);
    }

    @Transactional
    public boolean deactivateAssistantByUsername(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        String cleanUsername = username.startsWith("@") ? username.substring(1) : username;

        List<Assistant> active = getActiveAssistants();
        Optional<Assistant> target = active.stream()
            .filter(a -> {
                String u = a.getUsername();
                if (u == null || u.isBlank()) {
                    return false;
                }
                String cu = u.startsWith("@") ? u.substring(1) : u;
                return cleanUsername.equalsIgnoreCase(cu);
            })
            .findFirst();

        if (target.isEmpty()) {
            return false;
        }

        Assistant assistant = target.get();
        assistant.setActive(false);
        assistant.setUpdatedAt(LocalDateTime.now());
        assistantRepository.save(assistant);
        return true;
    }
}

