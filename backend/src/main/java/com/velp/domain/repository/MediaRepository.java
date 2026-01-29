package com.velp.domain.repository;

import com.velp.domain.model.SubtitleLine;

import java.util.List;
import java.util.Optional;

public interface MediaRepository {
    void saveTaskStatus(String taskId, String status, int progress, String videoId, String error, String url, String title);
    Optional<TaskStatus> getTaskStatus(String taskId);
    List<TaskEntry> getAllTasks();
    
    record TaskStatus(String status, int progress, String videoId, String error, String url, String title) {}
    record TaskEntry(String taskId, String status, int progress, String videoId, String url, String title) {}
}
