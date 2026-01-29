package com.velp.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velp.common.constants.AppConstants;
import com.velp.domain.model.SubtitleLine;
import com.velp.domain.repository.MediaRepository;
import com.velp.domain.service.TranslationService;
import com.velp.infrastructure.external.YtDlpClient;
import com.velp.infrastructure.parser.SubtitleFileParser;
import com.velp.interfaces.rest.dto.CourseDetailResponse;
import com.velp.interfaces.rest.dto.SubtitleLineDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MediaApplicationService {

    @Value("${velp.storage.path:downloads}")
    private String storagePath;

    private final MediaRepository mediaRepository;
    private final YtDlpClient ytDlpClient;
    private final SubtitleFileParser subtitleParser;
    private final TranslationService translationService;

    public MediaApplicationService(MediaRepository mediaRepository, YtDlpClient ytDlpClient, SubtitleFileParser subtitleParser, TranslationService translationService) {
        this.mediaRepository = mediaRepository;
        this.ytDlpClient = ytDlpClient;
        this.subtitleParser = subtitleParser;
        this.translationService = translationService;
    }

    public String submitTask(String url) {
        // Check if a successful task already exists for this URL
        List<MediaRepository.TaskEntry> existingTasks = mediaRepository.getAllTasks();
        for (MediaRepository.TaskEntry task : existingTasks) {
            if (url.equals(task.url()) && AppConstants.TaskStatus.COMPLETED.equals(task.status())) {
                log.info("Task for URL {} already completed, returning existing taskId: {}", url, task.taskId());
                return task.taskId();
            }
        }

        String taskId = UUID.randomUUID().toString();
        String videoTitle = ytDlpClient.getVideoTitle(url);
        mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PENDING, 0, null, null, url, videoTitle);
        
        processVideoAsync(taskId, url, videoTitle);
        
        return taskId;
    }

    public MediaRepository.TaskStatus getTaskStatus(String taskId) {
        return mediaRepository.getTaskStatus(taskId)
                .orElse(new MediaRepository.TaskStatus(AppConstants.TaskStatus.FAILED, 0, null, "Task not found", "", "", 0L));
    }

    public List<MediaRepository.TaskEntry> getAllTasks() {
        return mediaRepository.getAllTasks();
    }

    public void deleteTask(String taskId) {
        mediaRepository.deleteTask(taskId);
    }

    public void deleteFailedTasks() {
        mediaRepository.deleteFailedTasks();
    }

    public CourseDetailResponse getCourseDetail(String videoId) {
        File videoDir = new File(storagePath, videoId);
        if (!videoDir.exists()) {
            throw new RuntimeException("Video not found");
        }

        File[] videoFiles = videoDir.listFiles((dir, name) -> name.startsWith(AppConstants.Storage.VIDEO_PREFIX) 
                && !name.endsWith(AppConstants.Storage.VTT_EXT) 
                && !name.endsWith(AppConstants.Storage.JSON_EXT));
        String videoFileName = (videoFiles != null && videoFiles.length > 0) ? videoFiles[0].getName() : "video" + AppConstants.Storage.MP4_EXT;

        File subsFile = new File(videoDir, AppConstants.Storage.SUBS_JSON);
        List<SubtitleLine> subtitles = new ArrayList<>();
        if (subsFile.exists()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                subtitles = mapper.readValue(subsFile, mapper.getTypeFactory().constructCollectionType(List.class, SubtitleLine.class));
            } catch (Exception e) {
                log.error("Failed to read subtitles file: {}", subsFile.getAbsolutePath(), e);
            }
        }

        List<SubtitleLineDto> dtos = subtitles.stream()
                .map(s -> new SubtitleLineDto(s.getStartTime(), s.getEndTime(), s.getEn(), s.getCn()))
                .collect(Collectors.toList());
        
        // Get title from repository if available
        String title = "YouTube Video";
        List<MediaRepository.TaskEntry> allTasks = mediaRepository.getAllTasks();
        for (MediaRepository.TaskEntry t : allTasks) {
            if (videoId.equals(t.videoId())) {
                title = t.title();
                break;
            }
        }

        return new CourseDetailResponse(title, "/downloads/" + videoId + "/" + videoFileName, dtos);
    }

    @Async
    public void processVideoAsync(String taskId, String url, String title) {
        mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, 10, null, AppConstants.Messages.INIT, url, title);

        String videoId = UUID.randomUUID().toString();
        File outputDir = new File(storagePath, videoId);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        try {
            mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, 20, null, AppConstants.Messages.DOWNLOADING_YT, url, title);
            String outputTemplate = new File(outputDir, AppConstants.YtDlp.OUTPUT_TEMPLATE_BASE).getAbsolutePath();
            ytDlpClient.downloadVideo(url, outputTemplate, (progress) -> {
                // Map yt-dlp progress (0-80) to task progress (20-70)
                int taskProgress = 20 + (progress * 50 / 100);
                mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, taskProgress, null, AppConstants.Messages.DOWNLOADING_PROGRESS + progress + "%", url, title);
            });

            mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, 75, null, AppConstants.Messages.PARSING_SUBS, url, title);
            
            // Process Subtitles
            processSubtitles(taskId, outputDir, url, title);

            mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.COMPLETED, 100, videoId, AppConstants.Messages.PARSE_COMPLETE, url, title);

        } catch (Exception e) {
            log.error("Task {} failed", taskId, e);
            mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.FAILED, 0, null, "错误: " + e.getMessage(), url, title);
        }
    }

    private void processSubtitles(String taskId, File outputDir, String url, String title) throws IOException {
        File[] vttFiles = outputDir.listFiles((dir, name) -> name.endsWith(AppConstants.Storage.VTT_EXT));
        List<SubtitleLine> finalSubs = new ArrayList<>();

        if (vttFiles != null) {
            List<SubtitleLine> enSubs = new ArrayList<>();
            List<SubtitleLine> cnSubs = new ArrayList<>();

            for (File f : vttFiles) {
                if (f.getName().contains(AppConstants.Storage.EN_SUB_MARK)) {
                    enSubs = subtitleParser.parseVtt(f);
                } else if (f.getName().contains(AppConstants.Storage.ZH_SUB_MARK)) {
                    cnSubs = subtitleParser.parseVtt(f);
                }
            }
            
            if (enSubs.isEmpty() && !cnSubs.isEmpty()) {
                finalSubs = cnSubs;
            } else if (!enSubs.isEmpty()) {
                finalSubs = subtitleParser.mergeSubtitles(enSubs, cnSubs);
            }
        }

        // --- Translation Logic ---
        boolean needsTranslation = finalSubs.stream().anyMatch(s -> (s.getCn() == null || s.getCn().isEmpty()) && s.getEn() != null);
        
        if (needsTranslation) {
            mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, 80, null, AppConstants.Messages.TRANSLATION_STARTED, url, title);
            try {
                translationService.translate(finalSubs, (progress) -> {
                    // Map translation progress (0-100) to task progress (80-95)
                    int taskProgress = 80 + (progress * 15 / 100);
                    mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, taskProgress, null, AppConstants.Messages.TRANSLATION_PROGRESS + progress + "%", url, title);
                });
                mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, 95, null, AppConstants.Messages.TRANSLATION_COMPLETE, url, title);
            } catch (Exception e) {
                log.warn("Translation failed for task {}: {}", taskId, e.getMessage());
                mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, 90, null, AppConstants.Messages.TRANSLATION_FAILED, url, title);
            }
        }

        // Save to JSON
        ObjectMapper mapper = new ObjectMapper();
        File jsonFile = new File(outputDir, AppConstants.Storage.SUBS_JSON);
        mapper.writeValue(jsonFile, finalSubs);
    }
}
