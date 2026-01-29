package com.velp.infrastructure.repository;

import com.velp.domain.repository.MediaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryMediaRepository implements MediaRepository {

    private final Map<String, TaskStatus> taskMap = new ConcurrentHashMap<>();

    @Override
    public void saveTaskStatus(String taskId, String status, int progress, String videoId, String error, String url) {
        TaskStatus existing = taskMap.get(taskId);
        String finalUrl = (url != null) ? url : (existing != null ? existing.url() : "");
        taskMap.put(taskId, new TaskStatus(status, progress, videoId, error, finalUrl));
    }

    @Override
    public Optional<TaskStatus> getTaskStatus(String taskId) {
        return Optional.ofNullable(taskMap.get(taskId));
    }

    @Override
    public List<TaskEntry> getAllTasks() {
        return taskMap.entrySet().stream()
                .map(e -> new TaskEntry(e.getKey(), e.getValue().status(), e.getValue().progress(), e.getValue().videoId(), e.getValue().url()))
                .toList();
    }
}
