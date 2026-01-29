package com.velp.domain.repository;

import com.velp.domain.model.SubtitleLine;

import java.util.List;
import java.util.Optional;

public interface MediaRepository {
    void saveTaskStatus(String taskId, String status, int progress, String videoId, String error, String url);
    Optional<TaskStatus> getTaskStatus(String taskId);
    List<TaskEntry> getAllTasks();
    
    record TaskStatus(String status, int progress, String videoId, String error, String url) {}
    record TaskEntry(String taskId, String status, int progress, String videoId, String url) {}
}
