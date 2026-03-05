package com.example.m1nd.service;

import com.example.m1nd.model.Task;
import com.example.m1nd.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final Random random = new Random();

    public List<Task> findAll() {
        return taskRepository.findAll();
    }

    public Optional<Task> findRandom() {
        List<Task> all = taskRepository.findAll();
        if (all.isEmpty()) {
            return Optional.empty();
        }
        int idx = random.nextInt(all.size());
        return Optional.of(all.get(idx));
    }

    public Task createTask(String text, String type, Long createdBy) {
        Task task = new Task();
        task.setText(text);
        task.setType(type);
        task.setCreatedBy(createdBy);
        task.setCreatedAt(OffsetDateTime.now());
        return taskRepository.save(task);
    }

    public void deleteTask(Long id) {
        taskRepository.deleteById(id);
    }
}

