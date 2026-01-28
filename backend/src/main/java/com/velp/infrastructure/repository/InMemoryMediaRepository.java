package com.velp.infrastructure.repository;

import com.velp.domain.repository.MediaRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryMediaRepository implements MediaRepository {

    private final Map<String, TaskStatus> taskMap = new ConcurrentHashMap<>();

    @Override
    public void saveTaskStatus(String taskId, String status, int progress, String videoId, String error) {
        taskMap.put(taskId, new TaskStatus(status, progress, videoId, error));
    }

    @Override
    public Optional<TaskStatus> getTaskStatus(String taskId) {
        return Optional.ofNullable(taskMap.get(taskId));
    }
}
