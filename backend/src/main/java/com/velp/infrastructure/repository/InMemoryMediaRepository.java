package com.velp.infrastructure.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.velp.common.constants.AppConstants;
import com.velp.domain.repository.MediaRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Repository
public class InMemoryMediaRepository implements MediaRepository {

    @Value("${velp.storage.path:downloads}")
    private String storagePath;

    private static final String TASKS_FILE = "tasks.json";
    private final Map<String, TaskStatus> taskMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        loadTasks();
    }

    private void loadTasks() {
        File file = new File(storagePath, TASKS_FILE);
        if (file.exists()) {
            try {
                Map<String, TaskStatus> loaded = objectMapper.readValue(file, new TypeReference<Map<String, TaskStatus>>() {});
                taskMap.putAll(loaded);
                log.info("Loaded {} tasks from {}", taskMap.size(), file.getAbsolutePath());
            } catch (Exception e) {
                log.error("Failed to load tasks from persistence", e);
            }
        }
    }

    private synchronized void persistTasks() {
        try {
            File dir = new File(storagePath);
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, TASKS_FILE);
            objectMapper.writeValue(file, taskMap);
        } catch (Exception e) {
            log.error("Failed to persist tasks to disk", e);
        }
    }

    @Override
    public void saveTaskStatus(String taskId, String status, int progress, String videoId, String error, String url, String title) {
        TaskStatus existing = taskMap.get(taskId);
        String finalUrl = (url != null) ? url : (existing != null ? existing.url() : "");
        String finalTitle = (title != null) ? title : (existing != null ? existing.title() : "");
        long createdAt = (existing != null && existing.createdAt() > 0) ? existing.createdAt() : System.currentTimeMillis();
        
        taskMap.put(taskId, new TaskStatus(status, progress, videoId, error, finalUrl, finalTitle, createdAt));
        persistTasks();
    }

    @Override
    public void deleteTask(String taskId) {
        if (taskMap.remove(taskId) != null) {
            persistTasks();
        }
    }

    @Override
    public void deleteFailedTasks() {
        boolean removed = taskMap.entrySet().removeIf(entry -> AppConstants.TaskStatus.FAILED.equals(entry.getValue().status()));
        if (removed) {
            persistTasks();
        }
    }

    @Override
    public Optional<TaskStatus> getTaskStatus(String taskId) {
        return Optional.ofNullable(taskMap.get(taskId));
    }

    @Override
    public List<TaskEntry> getAllTasks() {
        return taskMap.entrySet().stream()
                .map(e -> new TaskEntry(
                        e.getKey(), 
                        e.getValue().status(), 
                        e.getValue().progress(), 
                        e.getValue().videoId(), 
                        e.getValue().url(), 
                        e.getValue().title(),
                        e.getValue().createdAt()))
                .toList();
    }
}
