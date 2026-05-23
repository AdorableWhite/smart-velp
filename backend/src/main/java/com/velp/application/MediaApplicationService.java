package com.velp.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.velp.common.VelpNotFoundException;
import com.velp.common.constants.AppConstants;
import com.velp.common.util.YouTubeUrlNormalizer;
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
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
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
    private final Executor mediaTaskExecutor;

    public MediaApplicationService(MediaRepository mediaRepository, YtDlpClient ytDlpClient, SubtitleFileParser subtitleParser, TranslationService translationService, Executor mediaTaskExecutor) {
        this.mediaRepository = mediaRepository;
        this.ytDlpClient = ytDlpClient;
        this.subtitleParser = subtitleParser;
        this.translationService = translationService;
        this.mediaTaskExecutor = mediaTaskExecutor;
    }

    public String submitTask(AnalyzeRequest request) {
        String url = YouTubeUrlNormalizer.normalize(request.getUrl());
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
                    && AppConstants.TaskStatus.COMPLETED.equals(task.status())
                    && Boolean.TRUE.equals(withAssetStatus(task).assetAvailable())) {
                log.info("Task for URL {} already completed, returning existing taskId: {}", url, task.taskId());
                return task.taskId();
            }
        }

        String taskId = UUID.randomUUID().toString();
        String videoTitle = "YouTube Video";
        mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PENDING, 0, null, null, url, videoTitle, sourceLang, targetLang);
        
        enqueueTask(taskId, () -> processVideoAsync(taskId, url, videoTitle, translationOptions), null, url, videoTitle, sourceLang, targetLang);
        
        return taskId;
    }

    public String submitLocalSubtitleTask(LocalSubtitleAnalyzeRequest request) {
        String title = request.getTitle() == null || request.getTitle().isBlank() ? "Local Subtitle Session" : request.getTitle();
        String sourceLang = normalizeLanguage(request.getSourceLang(), "en");
        String targetLang = normalizeLanguage(request.getTargetLang(), "zh-CN");
        TranslationOptions translationOptions = buildTranslationOptions(request, sourceLang, targetLang);

        String taskId = UUID.randomUUID().toString();
        mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PENDING, 0, null, null, "local://" + taskId, title, sourceLang, targetLang);
        enqueueTask(taskId, () -> processLocalSubtitleAsync(taskId, request, title, translationOptions), null, "local://" + taskId, title, sourceLang, targetLang);
        return taskId;
    }

    public String retranslateCourse(String videoId, CourseRetranslateRequest request) {
        File videoDir = new File(storagePath, videoId);
        if (!videoDir.exists()) {
            throw new VelpNotFoundException("课程资源不存在，可能已被清理。请回到资料库重新解析该视频。");
        }

        String sourceLang = normalizeLanguage(request.getSourceLang(), "en");
        String targetLang = normalizeLanguage(request.getTargetLang(), "zh-CN");
        TranslationOptions translationOptions = buildTranslationOptions(request, sourceLang, targetLang);

        String taskId = UUID.randomUUID().toString();
        String title = resolveCourseTitle(videoId);
        mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PENDING, 0, videoId, null, "course://" + videoId, title, sourceLang, targetLang);
        enqueueTask(taskId, () -> processCourseRetranslateAsync(taskId, videoId, title, translationOptions), videoId, "course://" + videoId, title, sourceLang, targetLang);
        return taskId;
    }

    private void enqueueTask(String taskId, Runnable task, String videoId, String url, String title, String sourceLang, String targetLang) {
        try {
            mediaTaskExecutor.execute(task);
        } catch (RejectedExecutionException e) {
            log.warn("Media task queue rejected task {}: {}", taskId, e.getMessage());
            mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.FAILED, 0, videoId,
                    "任务队列已满，请稍后重试。", url, title, sourceLang, targetLang);
        }
    }

    public MediaRepository.TaskStatus getTaskStatus(String taskId) {
        return mediaRepository.getTaskStatus(taskId)
                .orElse(new MediaRepository.TaskStatus(AppConstants.TaskStatus.FAILED, 0, null, "Task not found", "", "", "en", "zh-CN", 0L));
    }

    public List<MediaRepository.TaskEntry> getAllTasks() {
        return mediaRepository.getAllTasks().stream()
                .map(this::withAssetStatus)
                .toList();
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
            throw new VelpNotFoundException("课程资源不存在，可能已被清理。请回到资料库重新解析该视频。");
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

    private MediaRepository.TaskEntry withAssetStatus(MediaRepository.TaskEntry task) {
        if (!AppConstants.TaskStatus.COMPLETED.equals(task.status()) || task.videoId() == null || task.videoId().isBlank()) {
            return task;
        }

        File videoDir = new File(storagePath, task.videoId());
        if (!videoDir.exists()) {
            return taskWithAssetStatus(task, false, "课程资源不存在，可能已被清理。");
        }

        File subtitles = new File(videoDir, AppConstants.Storage.SUBS_JSON);
        if (!subtitles.exists()) {
            return taskWithAssetStatus(task, false, "字幕数据缺失，需要重新解析。");
        }

        File videoFile = findVideoFile(videoDir);
        if (requiresVideoFile(task.url()) && videoFile == null) {
            return taskWithAssetStatus(task, false, "视频文件缺失，需要重新下载。");
        }

        return taskWithAssetStatus(task, true, "课程资源可用");
    }

    private boolean requiresVideoFile(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        return !url.startsWith("local://") && !url.startsWith("course://");
    }

    private MediaRepository.TaskEntry taskWithAssetStatus(MediaRepository.TaskEntry task, boolean assetAvailable, String assetMessage) {
        return new MediaRepository.TaskEntry(
                task.taskId(),
                task.status(),
                task.progress(),
                task.videoId(),
                task.error(),
                task.url(),
                task.title(),
                task.sourceLang(),
                task.targetLang(),
                task.createdAt(),
                assetAvailable,
                assetMessage
        );
    }

    private File findVideoFile(File videoDir) {
        if (!videoDir.exists()) return null;
        File[] videoFiles = videoDir.listFiles((dir, name) -> name.startsWith(AppConstants.Storage.VIDEO_PREFIX) 
                && isPlayableVideoName(name));
        if (videoFiles == null || videoFiles.length == 0) {
            return null;
        }

        return List.of(videoFiles).stream()
                .max(Comparator.comparingInt(this::videoFileScore)
                        .thenComparingLong(File::length)
                        .thenComparing(File::getName))
                .orElse(null);
    }

    private boolean isPlayableVideoName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return !lower.endsWith(AppConstants.Storage.VTT_EXT)
                && !lower.endsWith(AppConstants.Storage.JSON_EXT)
                && !lower.endsWith(".part")
                && !lower.endsWith(".ytdl")
                && !lower.contains(".f")
                && (lower.endsWith(".mp4")
                    || lower.endsWith(".webm")
                    || lower.endsWith(".mkv")
                    || lower.endsWith(".m4v")
                    || lower.endsWith(".mov"));
    }

    private int videoFileScore(File file) {
        String lower = file.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp4")) {
            return 5;
        }
        if (lower.endsWith(".m4v")) {
            return 4;
        }
        if (lower.endsWith(".webm")) {
            return 3;
        }
        if (lower.endsWith(".mkv")) {
            return 2;
        }
        return 1;
    }

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
            ytDlpClient.downloadVideo(url, outputTemplate, sourceLang, targetLang, (progress) -> {
                // Map yt-dlp progress (0-80) to task progress (20-70)
                int taskProgress = 20 + (progress * 50 / 100);
                mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, taskProgress, null, AppConstants.Messages.DOWNLOADING_PROGRESS + progress + "%", url, title, sourceLang, targetLang);
            });

            String resolvedTitle = resolveDownloadedTitle(outputDir, title);
            mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, 75, null, AppConstants.Messages.PARSING_SUBS, url, resolvedTitle, sourceLang, targetLang);
            
            // Process Subtitles
            processSubtitles(taskId, outputDir, url, resolvedTitle, translationOptions);

            mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.COMPLETED, 100, videoId, AppConstants.Messages.PARSE_COMPLETE, url, resolvedTitle, sourceLang, targetLang);

        } catch (Exception e) {
            log.error("Task {} failed", taskId, e);
            cleanupFailedOutputDir(outputDir);
            mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.FAILED, 0, null, "错误: " + e.getMessage(), url, title, sourceLang, targetLang);
        }
    }

    private void cleanupFailedOutputDir(File outputDir) {
        if (outputDir == null || !outputDir.exists() || !outputDir.isDirectory()) {
            return;
        }

        try (var paths = Files.walk(outputDir.toPath())) {
            paths.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(file -> {
                        if (!file.delete()) {
                            log.debug("Failed to delete incomplete output file {}", file.getAbsolutePath());
                        }
                    });
        } catch (Exception e) {
            log.debug("Failed to cleanup incomplete output directory {}: {}", outputDir.getAbsolutePath(), e.getMessage());
        }
    }

    private String resolveDownloadedTitle(File outputDir, String fallback) {
        if (outputDir == null || !outputDir.exists()) {
            return fallback;
        }

        File[] infoFiles = outputDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".info.json"));
        if (infoFiles == null || infoFiles.length == 0) {
            return fallback;
        }

        File infoFile = List.of(infoFiles).stream()
                .max(Comparator.comparingLong(File::length).thenComparing(File::getName))
                .orElse(null);
        if (infoFile == null) {
            return fallback;
        }

        try {
            JsonNode info = new ObjectMapper().readTree(infoFile);
            JsonNode titleNode = info.get("title");
            if (titleNode != null && titleNode.isTextual() && !titleNode.asText().isBlank()) {
                return titleNode.asText();
            }
        } catch (Exception e) {
            log.debug("Failed to read yt-dlp info json {}: {}", infoFile.getAbsolutePath(), e.getMessage());
        }
        return fallback;
    }

    private void processSubtitles(String taskId, File outputDir, String url, String title, TranslationOptions translationOptions) throws IOException {
        String sourceLang = normalizeLanguage(translationOptions.getSourceLang(), "en");
        String targetLang = normalizeLanguage(translationOptions.getTargetLang(), "zh-CN");
        File[] vttFiles = outputDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(AppConstants.Storage.VTT_EXT));
        List<SubtitleLine> finalSubs = new ArrayList<>();

        if (vttFiles != null) {
            File sourceFile = findSubtitleFile(vttFiles, sourceLang, true);
            File targetFile = findSubtitleFile(vttFiles, targetLang, false);
            List<SubtitleLine> sourceSubs = sourceFile == null ? new ArrayList<>() : subtitleParser.parseVtt(sourceFile, sourceLang);
            List<SubtitleLine> targetSubs = targetFile == null ? new ArrayList<>() : subtitleParser.parseVtt(targetFile, targetLang);
            
            if (sourceSubs.isEmpty() && !targetSubs.isEmpty()) {
                finalSubs = targetSubs;
                finalSubs.forEach(line -> {
                    line.setTargetPayload(line.getEffectiveSourceText(), normalizeLanguage(line.getSourceLang(), targetLang));
                    line.setSourcePayload(line.getEffectiveSourceText(), normalizeLanguage(line.getSourceLang(), targetLang));
                });
            } else if (!sourceSubs.isEmpty()) {
                finalSubs = subtitleParser.mergeSubtitles(sourceSubs, targetSubs);
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
                fillMissingTargetsWithSource(finalSubs, targetLang);
                mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, 90, null, AppConstants.Messages.TRANSLATION_FAILED, url, title, sourceLang, targetLang);
            }
        }

        // Save to JSON
        ObjectMapper mapper = new ObjectMapper();
        File jsonFile = new File(outputDir, AppConstants.Storage.SUBS_JSON);
        mapper.writeValue(jsonFile, finalSubs);
    }

    private File findSubtitleFile(File[] files, String language, boolean preferManual) {
        if (files == null || files.length == 0) {
            return null;
        }

        String normalized = normalizeLanguage(language, "en").toLowerCase(Locale.ROOT);
        String baseLanguage = normalized.contains("-") ? normalized.substring(0, normalized.indexOf('-')) : normalized;

        return List.of(files).stream()
                .filter(file -> matchesSubtitleLanguage(file.getName(), normalized, baseLanguage))
                .sorted(Comparator.comparingInt(file -> subtitlePreferenceScore(file.getName(), normalized, preferManual)))
                .findFirst()
                .orElseGet(() -> preferManual
                        ? List.of(files).stream()
                                .sorted(Comparator.comparing(File::getName))
                                .findFirst()
                                .orElse(null)
                        : null);
    }

    private boolean matchesSubtitleLanguage(String fileName, String normalizedLanguage, String baseLanguage) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        return lowerName.contains("." + normalizedLanguage.toLowerCase(Locale.ROOT) + ".")
                || lowerName.contains("." + normalizedLanguage.toLowerCase(Locale.ROOT) + "-")
                || lowerName.contains("." + baseLanguage + ".")
                || lowerName.contains("." + baseLanguage + "-");
    }

    private int subtitlePreferenceScore(String fileName, String normalizedLanguage, boolean preferManual) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        int score = 0;
        if (lowerName.contains(".auto.")) {
            score += preferManual ? 8 : 2;
        }
        if (lowerName.contains("." + normalizedLanguage + ".")) {
            score -= 4;
        }
        if (lowerName.contains(".vtt")) {
            score -= 1;
        }
        return score;
    }

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

    public void processCourseRetranslateAsync(String taskId, String videoId, String title, TranslationOptions translationOptions) {
        String sourceLang = normalizeLanguage(translationOptions.getSourceLang(), "en");
        String targetLang = normalizeLanguage(translationOptions.getTargetLang(), "zh-CN");
        mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, 10, videoId, AppConstants.Messages.INIT, "course://" + videoId, title, sourceLang, targetLang);

        try {
            File outputDir = new File(storagePath, videoId);
            File jsonFile = new File(outputDir, AppConstants.Storage.SUBS_JSON);
            if (!jsonFile.exists()) {
                throw new RuntimeException("Subtitle data not found");
            }

            ObjectMapper mapper = new ObjectMapper();
            List<SubtitleLine> subtitles = mapper.readValue(
                    jsonFile,
                    mapper.getTypeFactory().constructCollectionType(List.class, SubtitleLine.class)
            );

            subtitles.forEach(line -> line.setTargetPayload("", targetLang));
            maybeTranslateSubtitles(taskId, subtitles, "course://" + videoId, title, translationOptions);
            mapper.writeValue(jsonFile, subtitles);

            mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.COMPLETED, 100, videoId, AppConstants.Messages.PARSE_COMPLETE, "course://" + videoId, title, sourceLang, targetLang);
        } catch (Exception e) {
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
            fillMissingTargetsWithSource(subtitles, targetLang);
            mediaRepository.saveTaskStatus(taskId, AppConstants.TaskStatus.PROCESSING, 90, null, AppConstants.Messages.TRANSLATION_FAILED, url, title, sourceLang, targetLang);
        }
    }

    private void fillMissingTargetsWithSource(List<SubtitleLine> subtitles, String targetLang) {
        if (subtitles == null) {
            return;
        }

        String normalizedTargetLang = normalizeLanguage(targetLang, "zh-CN");
        for (SubtitleLine subtitle : subtitles) {
            String targetText = subtitle.getEffectiveTargetText();
            String sourceText = subtitle.getEffectiveSourceText();
            if ((targetText == null || targetText.isBlank()) && sourceText != null && !sourceText.isBlank()) {
                subtitle.setTargetPayload(sourceText, normalizedTargetLang);
            }
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
