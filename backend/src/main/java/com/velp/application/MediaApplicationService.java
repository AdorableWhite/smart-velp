package com.velp.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velp.common.constants.AppConstants;
import com.velp.domain.model.SubtitleLine;
import com.velp.domain.repository.MediaRepository;
import com.velp.domain.service.TranslationOptions;
import com.velp.domain.service.TranslationService;
import com.velp.infrastructure.external.YtDlpClient;
import com.velp.infrastructure.parser.SubtitleFileParser;
import com.velp.interfaces.rest.dto.AnalyzeRequest;
import com.velp.interfaces.rest.dto.CourseRetranslateRequest;
import com.velp.interfaces.rest.dto.CourseDetailResponse;
import com.velp.interfaces.rest.dto.LocalSubtitleAnalyzeRequest;
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

    public String submitTask(AnalyzeRequest request) {
        String url = request.getUrl();
        String sourceLang = normalizeLanguage(request.getSourceLang(), "en");
        String targetLang = normalizeLanguage(request.getTargetLang(), "zh-CN");
        TranslationOptions translationOptions = buildTranslationOptions(request, sourceLang, targetLang);

        boolean allowReuse = request.getTranslationProfile() == null;

        // Check if a successful task already exists for the same request shape.
        List<MediaRepository.TaskEntry> existingTasks = mediaRepository.getAllTasks();
        for (MediaRepository.TaskEntry task : existingTasks) {
            boolean sameLanguages = sourceLang.equalsIgnoreCase(normalizeLanguage(task.sourceLang(), "en"))
                    && targetLang.equalsIgnoreCase(normalizeLanguage(task.targetLang(), "zh-CN"));

            if (allowReuse
                    && sameLanguages
                    && url.equals(task.url())
                    && AppConstants.TaskStatus.COMPLETED.equals(task.status())) {
                log.info("Task for URL {} already completed, returning existing taskId: {}", url, task.taskId());
                return task.taskId();
            }
        }

        String taskId = UUID.randomUUID().toString();
        String videoTitle = ytDlpClient.getVideoTitle(url);
        mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PENDING, 0, null, null, url, videoTitle, sourceLang, targetLang);
        
        processVideoAsync(taskId, url, videoTitle, translationOptions);
        
        return taskId;
    }

    public String submitLocalSubtitleTask(LocalSubtitleAnalyzeRequest request) {
        String title = request.getTitle() == null || request.getTitle().isBlank() ? "Local Subtitle Session" : request.getTitle();
        String sourceLang = normalizeLanguage(request.getSourceLang(), "en");
        String targetLang = normalizeLanguage(request.getTargetLang(), "zh-CN");
        TranslationOptions translationOptions = buildTranslationOptions(request, sourceLang, targetLang);

        String taskId = UUID.randomUUID().toString();
        mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PENDING, 0, null, null, "local://" + taskId, title, sourceLang, targetLang);
        processLocalSubtitleAsync(taskId, request, title, translationOptions);
        return taskId;
    }

    public String retranslateCourse(String videoId, CourseRetranslateRequest request) {
        File videoDir = new File(storagePath, videoId);
        if (!videoDir.exists()) {
            throw new RuntimeException("Course not found");
        }

        String sourceLang = normalizeLanguage(request.getSourceLang(), "en");
        String targetLang = normalizeLanguage(request.getTargetLang(), "zh-CN");
        TranslationOptions translationOptions = buildTranslationOptions(request, sourceLang, targetLang);

        String taskId = UUID.randomUUID().toString();
        String title = resolveCourseTitle(videoId);
        mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PENDING, 0, videoId, null, "course://" + videoId, title, sourceLang, targetLang);
        processCourseRetranslateAsync(taskId, videoId, title, translationOptions);
        return taskId;
    }

    public MediaRepository.TaskStatus getTaskStatus(String taskId) {
        return mediaRepository.getTaskStatus(taskId)
                .orElse(new MediaRepository.TaskStatus(AppConstants.TaskStatus.FAILED, 0, null, "Task not found", "", "", "en", "zh-CN", 0L));
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

        File videoFile = findVideoFile(videoDir);
        String videoFileName = videoFile != null ? videoFile.getName() : "video" + AppConstants.Storage.MP4_EXT;

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

        String title = resolveCourseTitle(videoId);
        String sourceLang = resolveCourseSourceLang(videoId);
        String targetLang = resolveCourseTargetLang(videoId);

        final String resolvedSourceLang = sourceLang;
        final String resolvedTargetLang = targetLang;

        List<SubtitleLineDto> dtos = subtitles.stream()
                .map(s -> new SubtitleLineDto(
                        s.getStartTime(),
                        s.getEndTime(),
                        s.getEn(),
                        s.getCn(),
                        s.getEffectiveSourceText(),
                        s.getEffectiveTargetText(),
                        normalizeLanguage(s.getSourceLang(), resolvedSourceLang),
                        normalizeLanguage(s.getTargetLang(), resolvedTargetLang)
                ))
                .collect(Collectors.toList());

        boolean hasVideo = videoFile != null;
        String videoUrl = hasVideo ? "/downloads/" + videoId + "/" + videoFileName : "";
        return new CourseDetailResponse(title, videoUrl, dtos, resolvedSourceLang, resolvedTargetLang, hasVideo);
    }

    public File getVideoFile(String videoId) {
        File videoDir = new File(storagePath, videoId);
        return findVideoFile(videoDir);
    }

    private File findVideoFile(File videoDir) {
        if (!videoDir.exists()) return null;
        File[] videoFiles = videoDir.listFiles((dir, name) -> name.startsWith(AppConstants.Storage.VIDEO_PREFIX) 
                && !name.endsWith(AppConstants.Storage.VTT_EXT) 
                && !name.endsWith(AppConstants.Storage.JSON_EXT));
        return (videoFiles != null && videoFiles.length > 0) ? videoFiles[0] : null;
    }

    @Async
    public void processVideoAsync(String taskId, String url, String title, TranslationOptions translationOptions) {
        String sourceLang = normalizeLanguage(translationOptions.getSourceLang(), "en");
        String targetLang = normalizeLanguage(translationOptions.getTargetLang(), "zh-CN");
        mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, 10, null, AppConstants.Messages.INIT, url, title, sourceLang, targetLang);

        String videoId = UUID.randomUUID().toString();
        File outputDir = new File(storagePath, videoId);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        try {
            mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, 20, null, AppConstants.Messages.DOWNLOADING_YT, url, title, sourceLang, targetLang);
            String outputTemplate = new File(outputDir, AppConstants.YtDlp.OUTPUT_TEMPLATE_BASE).getAbsolutePath();
            ytDlpClient.downloadVideo(url, outputTemplate, (progress) -> {
                // Map yt-dlp progress (0-80) to task progress (20-70)
                int taskProgress = 20 + (progress * 50 / 100);
                mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, taskProgress, null, AppConstants.Messages.DOWNLOADING_PROGRESS + progress + "%", url, title, sourceLang, targetLang);
            });

            mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, 75, null, AppConstants.Messages.PARSING_SUBS, url, title, sourceLang, targetLang);
            
            // Process Subtitles
            processSubtitles(taskId, outputDir, url, title, translationOptions);

            mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.COMPLETED, 100, videoId, AppConstants.Messages.PARSE_COMPLETE, url, title, sourceLang, targetLang);

        } catch (Exception e) {
            log.error("Task {} failed", taskId, e);
            mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.FAILED, 0, null, "错误: " + e.getMessage(), url, title, sourceLang, targetLang);
        }
    }

    private void processSubtitles(String taskId, File outputDir, String url, String title, TranslationOptions translationOptions) throws IOException {
        String sourceLang = normalizeLanguage(translationOptions.getSourceLang(), "en");
        String targetLang = normalizeLanguage(translationOptions.getTargetLang(), "zh-CN");
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
        boolean needsTranslation = finalSubs.stream()
                .anyMatch(s -> (s.getEffectiveTargetText() == null || s.getEffectiveTargetText().isEmpty())
                        && s.getEffectiveSourceText() != null);
        
        if (needsTranslation) {
            mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, 80, null, AppConstants.Messages.TRANSLATION_STARTED, url, title, sourceLang, targetLang);
            try {
                translationService.translate(finalSubs, translationOptions, (progress) -> {
                    // Map translation progress (0-100) to task progress (80-95)
                    int taskProgress = 80 + (progress * 15 / 100);
                    mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, taskProgress, null, AppConstants.Messages.TRANSLATION_PROGRESS + progress + "%", url, title, sourceLang, targetLang);
                });
                mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, 95, null, AppConstants.Messages.TRANSLATION_COMPLETE, url, title, sourceLang, targetLang);
            } catch (Exception e) {
                log.warn("Translation failed for task {}: {}", taskId, e.getMessage());
                mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, 90, null, AppConstants.Messages.TRANSLATION_FAILED, url, title, sourceLang, targetLang);
            }
        }

        // Save to JSON
        ObjectMapper mapper = new ObjectMapper();
        File jsonFile = new File(outputDir, AppConstants.Storage.SUBS_JSON);
        mapper.writeValue(jsonFile, finalSubs);
    }

    @Async
    public void processLocalSubtitleAsync(String taskId, LocalSubtitleAnalyzeRequest request, String title, TranslationOptions translationOptions) {
        String sourceLang = normalizeLanguage(translationOptions.getSourceLang(), "en");
        String targetLang = normalizeLanguage(translationOptions.getTargetLang(), "zh-CN");
        mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, 10, null, AppConstants.Messages.INIT, "local://" + taskId, title, sourceLang, targetLang);

        String videoId = UUID.randomUUID().toString();
        File outputDir = new File(storagePath, videoId);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        try {
            mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, 35, null, AppConstants.Messages.PARSING_SUBS, "local://" + taskId, title, sourceLang, targetLang);
            List<SubtitleLine> subtitles = subtitleParser.parseContent(request.getSubtitleContent(), request.getSubtitleFormat(), sourceLang);
            maybeTranslateSubtitles(taskId, subtitles, "local://" + taskId, title, translationOptions);

            ObjectMapper mapper = new ObjectMapper();
            File jsonFile = new File(outputDir, AppConstants.Storage.SUBS_JSON);
            mapper.writeValue(jsonFile, subtitles);

            mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.COMPLETED, 100, videoId, AppConstants.Messages.PARSE_COMPLETE, "local://" + taskId, title, sourceLang, targetLang);
        } catch (Exception e) {
            log.error("Local subtitle task {} failed", taskId, e);
            mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.FAILED, 0, null, "错误: " + e.getMessage(), "local://" + taskId, title, sourceLang, targetLang);
        }
    }

    @Async
    public void processCourseRetranslateAsync(String taskId, String videoId, String title, TranslationOptions translationOptions) {
        String sourceLang = normalizeLanguage(translationOptions.getSourceLang(), "en");
        String targetLang = normalizeLanguage(translationOptions.getTargetLang(), "zh-CN");
        mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, 10, videoId, AppConstants.Messages.INIT, "course://" + videoId, title, sourceLang, targetLang);

        File outputDir = new File(storagePath, videoId);
        File jsonFile = new File(outputDir, AppConstants.Storage.SUBS_JSON);
        if (!jsonFile.exists()) {
            throw new RuntimeException("Subtitle data not found");
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            List<SubtitleLine> subtitles = mapper.readValue(
                    jsonFile,
                    mapper.getTypeFactory().constructCollectionType(List.class, SubtitleLine.class)
            );

            subtitles.forEach(line -> line.setTargetPayload("", targetLang));
            maybeTranslateSubtitles(taskId, subtitles, "course://" + videoId, title, translationOptions);
            mapper.writeValue(jsonFile, subtitles);

            mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.COMPLETED, 100, videoId, AppConstants.Messages.PARSE_COMPLETE, "course://" + videoId, title, sourceLang, targetLang);
        } catch (IOException e) {
            log.error("Retranslate task {} failed", taskId, e);
            mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.FAILED, 0, videoId, "错误: " + e.getMessage(), "course://" + videoId, title, sourceLang, targetLang);
        }
    }

    private void maybeTranslateSubtitles(String taskId, List<SubtitleLine> subtitles, String url, String title, TranslationOptions translationOptions) {
        String sourceLang = normalizeLanguage(translationOptions.getSourceLang(), "en");
        String targetLang = normalizeLanguage(translationOptions.getTargetLang(), "zh-CN");
        boolean needsTranslation = subtitles.stream()
                .anyMatch(s -> (s.getEffectiveTargetText() == null || s.getEffectiveTargetText().isEmpty())
                        && s.getEffectiveSourceText() != null);

        if (!needsTranslation) {
            return;
        }

        mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, 80, null, AppConstants.Messages.TRANSLATION_STARTED, url, title, sourceLang, targetLang);
        try {
            translationService.translate(subtitles, translationOptions, (progress) -> {
                int taskProgress = 80 + (progress * 15 / 100);
                mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, taskProgress, null, AppConstants.Messages.TRANSLATION_PROGRESS + progress + "%", url, title, sourceLang, targetLang);
            });
            mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, 95, null, AppConstants.Messages.TRANSLATION_COMPLETE, url, title, sourceLang, targetLang);
        } catch (Exception e) {
            log.warn("Translation failed for task {}: {}", taskId, e.getMessage());
            mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, 90, null, AppConstants.Messages.TRANSLATION_FAILED, url, title, sourceLang, targetLang);
        }
    }

    private String normalizeLanguage(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private TranslationOptions buildTranslationOptions(AnalyzeRequest request, String sourceLang, String targetLang) {
        if (request == null || request.getTranslationProfile() == null) {
            return TranslationOptions.builder()
                    .sourceLang(sourceLang)
                    .targetLang(targetLang)
                    .build();
        }

        return TranslationOptions.builder()
                .sourceLang(sourceLang)
                .targetLang(targetLang)
                .provider(request.getTranslationProfile().getProvider())
                .baseUrl(request.getTranslationProfile().getBaseUrl())
                .model(request.getTranslationProfile().getModel())
                .apiKey(request.getTranslationProfile().getApiKey())
                .prompt(request.getTranslationProfile().getPrompt())
                .build();
    }

    private TranslationOptions buildTranslationOptions(LocalSubtitleAnalyzeRequest request, String sourceLang, String targetLang) {
        if (request == null || request.getTranslationProfile() == null) {
            return TranslationOptions.builder()
                    .sourceLang(sourceLang)
                    .targetLang(targetLang)
                    .build();
        }

        return TranslationOptions.builder()
                .sourceLang(sourceLang)
                .targetLang(targetLang)
                .provider(request.getTranslationProfile().getProvider())
                .baseUrl(request.getTranslationProfile().getBaseUrl())
                .model(request.getTranslationProfile().getModel())
                .apiKey(request.getTranslationProfile().getApiKey())
                .prompt(request.getTranslationProfile().getPrompt())
                .build();
    }

    private TranslationOptions buildTranslationOptions(CourseRetranslateRequest request, String sourceLang, String targetLang) {
        if (request == null || request.getTranslationProfile() == null) {
            return TranslationOptions.builder()
                    .sourceLang(sourceLang)
                    .targetLang(targetLang)
                    .build();
        }

        return TranslationOptions.builder()
                .sourceLang(sourceLang)
                .targetLang(targetLang)
                .provider(request.getTranslationProfile().getProvider())
                .baseUrl(request.getTranslationProfile().getBaseUrl())
                .model(request.getTranslationProfile().getModel())
                .apiKey(request.getTranslationProfile().getApiKey())
                .prompt(request.getTranslationProfile().getPrompt())
                .build();
    }

    private String resolveCourseTitle(String videoId) {
        for (MediaRepository.TaskEntry task : mediaRepository.getAllTasks()) {
            if (videoId.equals(task.videoId())) {
                return task.title();
            }
        }
        return "YouTube Video";
    }

    private String resolveCourseSourceLang(String videoId) {
        for (MediaRepository.TaskEntry task : mediaRepository.getAllTasks()) {
            if (videoId.equals(task.videoId())) {
                return normalizeLanguage(task.sourceLang(), "en");
            }
        }
        return "en";
    }

    private String resolveCourseTargetLang(String videoId) {
        for (MediaRepository.TaskEntry task : mediaRepository.getAllTasks()) {
            if (videoId.equals(task.videoId())) {
                return normalizeLanguage(task.targetLang(), "zh-CN");
            }
        }
        return "zh-CN";
    }
}
