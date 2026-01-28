package com.velp.interfaces.rest;

import com.velp.application.MediaApplicationService;
import com.velp.common.constants.AppConstants;
import com.velp.domain.repository.MediaRepository;
import com.velp.interfaces.rest.dto.CourseDetailResponse;
import com.velp.interfaces.rest.dto.ParserStatusResponse;
import com.velp.interfaces.rest.dto.TaskResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
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

    @GetMapping("/course/{videoId}/detail")
    public CourseDetailResponse getCourseDetail(@PathVariable String videoId) {
        return mediaApplicationService.getCourseDetail(videoId);
    }
}
