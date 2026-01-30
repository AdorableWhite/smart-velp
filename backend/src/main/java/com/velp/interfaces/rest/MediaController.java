package com.velp.interfaces.rest;

import com.velp.application.MediaApplicationService;
import com.velp.common.constants.AppConstants;
import com.velp.domain.repository.MediaRepository;
import com.velp.interfaces.rest.dto.CourseDetailResponse;
import com.velp.interfaces.rest.dto.ParserStatusResponse;
import com.velp.interfaces.rest.dto.TaskResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MediaController {

    private final MediaApplicationService mediaApplicationService;

    public MediaController(MediaApplicationService mediaApplicationService) {
        this.mediaApplicationService = mediaApplicationService;
    }

    @PostMapping("/parser/analyze")
    public TaskResponse analyze(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("URL is required");
        }
        String taskId = mediaApplicationService.submitTask(url);
        return new TaskResponse(taskId, AppConstants.TaskStatus.PROCESSING, AppConstants.Messages.TASK_SUBMITTED);
    }

    @GetMapping("/parser/status/{taskId}")
    public ParserStatusResponse getStatus(@PathVariable String taskId) {
        MediaRepository.TaskStatus status = mediaApplicationService.getTaskStatus(taskId);
        return new ParserStatusResponse(status.status(), status.progress(), status.videoId(), status.error());
    }

    @GetMapping("/parser/tasks")
    public List<MediaRepository.TaskEntry> getAllTasks() {
        return mediaApplicationService.getAllTasks();
    }

    @DeleteMapping("/parser/tasks/{taskId}")
    public void deleteTask(@PathVariable String taskId) {
        mediaApplicationService.deleteTask(taskId);
    }

    @DeleteMapping("/parser/tasks/failed")
    public void deleteFailedTasks() {
        mediaApplicationService.deleteFailedTasks();
    }

    @GetMapping("/course/{videoId}/detail")
    public CourseDetailResponse getCourseDetail(@PathVariable String videoId) {
        return mediaApplicationService.getCourseDetail(videoId);
    }

    @GetMapping("/course/{videoId}/download")
    public ResponseEntity<Resource> downloadVideo(@PathVariable String videoId) {
        File videoFile = mediaApplicationService.getVideoFile(videoId);
        if (videoFile == null || !videoFile.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(videoFile);
        String filename = videoFile.getName();
        
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("video/mp4"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }
}
