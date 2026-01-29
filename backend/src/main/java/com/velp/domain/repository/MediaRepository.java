package com.velp.domain.repository;

import java.util.List;
import java.util.Optional;

public interface MediaRepository {
    void saveTaskStatus(String taskId, String status, int progress, String videoId, String error, String url, String title);
    void deleteTask(String taskId);
    void deleteFailedTasks();
    Optional<TaskStatus> getTaskStatus(String taskId);
    List<TaskEntry> getAllTasks();
    
    record TaskStatus(String status, int progress, String videoId, String error, String url, String title, long createdAt) {}
    record TaskEntry(String taskId, String status, int progress, String videoId, String url, String title, long createdAt) {}
}
