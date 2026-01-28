package com.velp.domain.repository;

import com.velp.domain.model.SubtitleLine;

import java.util.List;
import java.util.Optional;

public interface MediaRepository {
    void saveTaskStatus(String taskId, String status, int progress, String videoId, String error);
    Optional<TaskStatus> getTaskStatus(String taskId);
    
    // In a real app, we'd save Media entities. 
    // For now, we just need to know if a video exists on disk (Infrastructure concern) 
    // or return metadata.
    
    record TaskStatus(String status, int progress, String videoId, String error) {}
}
