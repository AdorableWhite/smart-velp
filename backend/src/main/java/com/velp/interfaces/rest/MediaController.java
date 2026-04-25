package com.velp.interfaces.rest;

import com.velp.application.MediaApplicationService;
import com.velp.common.constants.AppConstants;
import com.velp.domain.repository.MediaRepository;
import com.velp.interfaces.rest.dto.AnalyzeRequest;
import com.velp.interfaces.rest.dto.CourseRetranslateRequest;
import com.velp.interfaces.rest.dto.CourseDetailResponse;
import com.velp.interfaces.rest.dto.LocalSubtitleAnalyzeRequest;
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

@RestController
@RequestMapping("/api")
public class MediaController {

    private final MediaApplicationService mediaApplicationService;

    public MediaController(MediaApplicationService mediaApplicationService) {
        this.mediaApplicationService = mediaApplicationService;
    }

    @PostMapping("/parser/analyze")
    public TaskResponse analyze(@RequestBody AnalyzeRequest body) {
        String url = body.getUrl();
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("URL is required");
        }
        String taskId = mediaApplicationService.submitTask(body);
        return new TaskResponse(taskId, AppConstants.TaskStatus.PROCESSING, AppConstants.Messages.TASK_SUBMITTED);
    }

    @PostMapping("/parser/local-subtitles")
    public TaskResponse analyzeLocalSubtitles(@RequestBody LocalSubtitleAnalyzeRequest body) {
        if (body.getSubtitleContent() == null || body.getSubtitleContent().isBlank()) {
            throw new IllegalArgumentException("Subtitle content is required");
        }

        String taskId = mediaApplicationService.submitLocalSubtitleTask(body);
        return new TaskResponse(taskId, AppConstants.TaskStatus.PROCESSING, AppConstants.Messages.TASK_SUBMITTED);
    }

    @GetMapping("/parser/status/{taskId}")
    public ParserStatusResponse getStatus(@PathVariable String taskId) {
        MediaRepository.TaskStatus status = mediaApplicationService.getTaskStatus(taskId);
        return new ParserStatusResponse(
                status.status(),
                status.progress(),
                status.videoId(),
                status.error(),
                status.title(),
                status.sourceLang(),
                status.targetLang()
        );
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

    @PostMapping("/course/{videoId}/retranslate")
    public TaskResponse retranslateCourse(@PathVariable String videoId, @RequestBody CourseRetranslateRequest body) {
        String taskId = mediaApplicationService.retranslateCourse(videoId, body);
        return new TaskResponse(taskId, AppConstants.TaskStatus.PROCESSING, AppConstants.Messages.TASK_SUBMITTED);
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
