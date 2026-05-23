package com.velp.infrastructure.external;

import com.velp.common.constants.AppConstants;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Component
public class YtDlpClient {

    @Value("${velp.ytdlp.path:yt-dlp}")
    private String ytDlpPath;

    @Value("${velp.ytdlp.timeout-minutes:20}")
    private long timeoutMinutes;

    @Value("${velp.ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    @Value("${velp.ytdlp.cookies.path:}")
    private String cookiesPath;

    @Value("${velp.ytdlp.cookies-from-browser:}")
    private String cookiesFromBrowser;

    @Value("${velp.ytdlp.force-ipv4:false}")
    private boolean forceIpv4;

    private String resolvedYtDlpPath;
    private String resolvedFfmpegPath;
    private boolean ffmpegAvailable;

    @PostConstruct
    public void init() {
        resolvedYtDlpPath = resolveYtDlpPath();
        resolvedFfmpegPath = resolveFfmpegPath();
        ffmpegAvailable = resolvedFfmpegPath != null;
        String version = tryGetVersion(resolvedYtDlpPath);
        if (version != null) {
            log.info("Initialized YtDlpClient using {} (version: {}, ffmpeg: {})", resolvedYtDlpPath, version, ffmpegAvailable ? resolvedFfmpegPath : "not found");
        } else {
            log.warn("Failed to initialize yt-dlp. Tried: {}", resolvedYtDlpPath);
        }
    }

    public String getVideoTitle(String url) {
        try {
            List<String> command = new ArrayList<>();
            command.add(resolvedYtDlpPath);
            command.add("--print");
            command.add("%(title)s");
            command.add("--no-warnings");
            command.add("--no-playlist");
            command.add("--ignore-errors");
            command.add("--no-check-certificates");
            command.add("--extractor-args");
            command.add(AppConstants.YtDlp.EXTRACTOR_ARGS_YT);
            command.add(url);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            List<String> outputLines = Collections.synchronizedList(new ArrayList<>());
            CompletableFuture<Void> outputReader = CompletableFuture.runAsync(() -> readProcessOutput(process, outputLines, null));

            boolean finished = process.waitFor(45, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Timed out getting video title for {}", url);
                return "Unknown Video";
            }
            outputReader.join();

            synchronized (outputLines) {
                for (String line : outputLines) {
                    if (isLikelyTitleLine(line)) {
                        return line.trim();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get video title for {}: {}", url, e.getMessage());
        }
        return "Unknown Video";
    }

    private boolean isLikelyTitleLine(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }

        String normalized = line.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        return !lower.startsWith("warning:")
                && !lower.startsWith("error:")
                && !lower.startsWith("[youtube]")
                && !lower.startsWith("[download]")
                && !lower.startsWith("it is strongly recommended")
                && !lower.contains("update yt-dlp")
                && !lower.contains("latest version");
    }

    public void downloadVideo(String url, String outputTemplate, String sourceLang, String targetLang, Consumer<Integer> progressCallback) throws IOException, InterruptedException {
        List<String> formats = buildFormatFallbacks();
        RuntimeException lastError = null;

        for (int i = 0; i < formats.size(); i++) {
            String format = formats.get(i);
            try {
                log.info("yt-dlp attempt {}/{} using format {}", i + 1, formats.size(), format);
                runDownload(url, outputTemplate, format, buildSubtitleLanguages(sourceLang, targetLang), progressCallback);
                return;
            } catch (RuntimeException e) {
                lastError = e;
                log.warn("yt-dlp attempt {}/{} failed: {}", i + 1, formats.size(), e.getMessage());
            }
        }

        throw lastError == null ? new RuntimeException("yt-dlp failed without details") : lastError;
    }

    public DownloadDiagnostics getDiagnostics() {
        return new DownloadDiagnostics(
                resolvedYtDlpPath,
                tryGetVersion(resolvedYtDlpPath),
                resolvedFfmpegPath,
                ffmpegAvailable,
                isCookieSourceConfigured(),
                describeCookieSource(),
                forceIpv4,
                timeoutMinutes,
                buildFormatFallbacks()
        );
    }

    private void runDownload(String url, String outputTemplate, String format, String subtitleLanguages, Consumer<Integer> progressCallback) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(resolvedYtDlpPath);
        command.add("-f");
        command.add(format);
        if (ffmpegAvailable) {
            command.add("--merge-output-format");
            command.add("mp4");
            command.add("--ffmpeg-location");
            command.add(resolveFfmpegLocation());
        }
        command.add("--write-sub");
        command.add("--write-auto-sub");
        command.add("--write-info-json");
        command.add("--sub-lang");
        command.add(subtitleLanguages);
        if (ffmpegAvailable) {
            command.add("--convert-subs");
            command.add("vtt");
        }
        command.add("--geo-bypass");
        addCookieArgs(command);
        if (forceIpv4) {
            command.add("--force-ipv4");
        }
        command.add("--retries");
        command.add("5");
        command.add("--fragment-retries");
        command.add("10");
        command.add("--retry-sleep");
        command.add("linear=1::2");
        command.add("--socket-timeout");
        command.add("30");
        command.add("--newline");
        command.add("--no-playlist");
        command.add("--no-cache-dir");
        command.add("--no-check-certificates");
        command.add("--force-overwrites");
        command.add("--extractor-args");
        command.add(AppConstants.YtDlp.EXTRACTOR_ARGS_YT);
        command.add("--referer");
        command.add(AppConstants.YtDlp.REFERER_YT);
        command.add("--output");
        command.add(outputTemplate);
        command.add(url);
        
        log.info("Executing command: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        List<String> outputLines = Collections.synchronizedList(new ArrayList<>());
        
        CompletableFuture<Void> outputReader = CompletableFuture.runAsync(() -> readProcessOutput(process, outputLines, progressCallback));

        boolean finished = process.waitFor(Math.max(1, timeoutMinutes), TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            String outputTail = tail(outputLines, 12);
            throw new RuntimeException(YtDlpFailureClassifier.classify(outputTail)
                    + "\n\n原始摘要：yt-dlp timed out after " + timeoutMinutes + " minutes. Last output: " + outputTail);
        }
        outputReader.join();
        
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            String fullOutput;
            synchronized (outputLines) {
                fullOutput = String.join("\n", outputLines);
            }
            log.error("yt-dlp failed. Output:\n{}", fullOutput);
            String outputTail = tail(outputLines, 12);
            throw new RuntimeException(YtDlpFailureClassifier.classify(fullOutput)
                    + "\n\n原始摘要：yt-dlp exited with code " + exitCode + ": " + outputTail);
        }
        progressCallback.accept(80);
    }

    private void readProcessOutput(Process process, List<String> outputLines, Consumer<Integer> progressCallback) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputLines.add(line);
                parseProgress(line, progressCallback);
            }
        } catch (IOException e) {
            log.debug("Failed reading yt-dlp output: {}", e.getMessage());
        }
    }

    private void parseProgress(String line, Consumer<Integer> progressCallback) {
        if (progressCallback == null || line == null) {
            return;
        }
        if (line.contains(AppConstants.YtDlp.DOWNLOAD_MARK) && line.contains(AppConstants.YtDlp.PERCENT_MARK)) {
            try {
                String[] parts = line.split("\\s+");
                for (String part : parts) {
                    if (part.endsWith(AppConstants.YtDlp.PERCENT_MARK)) {
                        String percentStr = part.substring(0, part.length() - 1);
                        double percent = Double.parseDouble(percentStr);
                        progressCallback.accept((int) percent);
                        break;
                    }
                }
            } catch (Exception e) {
                // Ignore progress parsing errors.
            }
        }
    }

    private List<String> buildFormatFallbacks() {
        if (ffmpegAvailable) {
            return Arrays.asList(
                    AppConstants.YtDlp.FORMAT_BEST_MERGE_MP4,
                    AppConstants.YtDlp.FORMAT_PROGRESSIVE_MP4,
                    AppConstants.YtDlp.FORMAT_ANY
            );
        }

        return Arrays.asList(
                AppConstants.YtDlp.FORMAT_PROGRESSIVE_MP4,
                "best[height<=720]/best",
                "worst[ext=mp4]/worst"
        );
    }

    private void addCookieArgs(List<String> command) {
        if (cookiesPath != null && !cookiesPath.isBlank()) {
            Path path = Paths.get(cookiesPath.trim());
            if (Files.exists(path) && Files.isRegularFile(path)) {
                command.add("--cookies");
                command.add(path.toAbsolutePath().toString());
                return;
            }
            log.warn("Configured yt-dlp cookies file does not exist: {}", cookiesPath);
        }

        if (cookiesFromBrowser != null && !cookiesFromBrowser.isBlank()) {
            command.add("--cookies-from-browser");
            command.add(cookiesFromBrowser.trim());
        }
    }

    private boolean isCookieSourceConfigured() {
        return (cookiesPath != null && !cookiesPath.isBlank() && Files.exists(Paths.get(cookiesPath.trim())))
                || (cookiesFromBrowser != null && !cookiesFromBrowser.isBlank());
    }

    private String describeCookieSource() {
        if (cookiesPath != null && !cookiesPath.isBlank() && Files.exists(Paths.get(cookiesPath.trim()))) {
            return "cookies.txt";
        }
        if (cookiesFromBrowser != null && !cookiesFromBrowser.isBlank()) {
            return "browser:" + cookiesFromBrowser.trim();
        }
        return "none";
    }

    private String buildSubtitleLanguages(String sourceLang, String targetLang) {
        List<String> languages = new ArrayList<>();
        addLanguageVariants(languages, sourceLang);
        addLanguageVariants(languages, targetLang);
        for (String lang : AppConstants.YtDlp.DEFAULT_SUB_LANGS.split(",")) {
            addLanguageVariants(languages, lang);
        }
        return String.join(",", languages);
    }

    private void addLanguageVariants(List<String> languages, String lang) {
        if (lang == null || lang.isBlank()) {
            return;
        }
        String normalized = lang.trim();
        addUnique(languages, normalized);
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("zh")) {
            addUnique(languages, "zh");
            addUnique(languages, "zh-CN");
            addUnique(languages, "zh-Hans");
            addUnique(languages, "zh-Hant");
        } else if (lower.contains("-")) {
            addUnique(languages, lower.substring(0, lower.indexOf('-')));
        }
    }

    private void addUnique(List<String> values, String value) {
        if (value != null && !value.isBlank() && values.stream().noneMatch(existing -> existing.equalsIgnoreCase(value))) {
            values.add(value);
        }
    }

    private String tail(List<String> lines, int maxLines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        synchronized (lines) {
            int from = Math.max(0, lines.size() - Math.max(1, maxLines));
            return String.join("\n", lines.subList(from, lines.size()));
        }
    }

    private String resolveYtDlpPath() {
        List<String> candidates = new ArrayList<>();
        
        // 1. 首先尝试配置的路径（环境变量或配置文件）
        if (ytDlpPath != null && !ytDlpPath.trim().isEmpty()) {
            candidates.add(ytDlpPath.trim());
        }

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        
        // 2. 在 Windows 上，尝试查找项目根目录下的 .tools/yt-dlp.exe
        if (isWindows) {
            String toolsPath = findToolsYtDlpPath();
            if (toolsPath != null) {
                candidates.add(toolsPath);
            }
        }
        
        // 3. 尝试系统 PATH 中的命令
        candidates.addAll(isWindows ? Arrays.asList("yt-dlp.exe", "yt-dlp") : Arrays.asList("yt-dlp", "yt-dlp.exe"));

        // 4. 按顺序尝试每个候选路径
        for (String candidate : candidates) {
            String version = tryGetVersion(candidate);
            if (version != null) {
                return candidate;
            }
        }

        // 5. 如果都失败，返回配置的路径（即使可能无效）
        return ytDlpPath != null ? ytDlpPath : (isWindows ? "yt-dlp.exe" : "yt-dlp");
    }
    
    /**
     * 查找项目根目录下的 .tools/yt-dlp.exe
     * 从 backend 目录向上查找项目根目录
     */
    private String findToolsYtDlpPath() {
        try {
            // 尝试多个可能的项目根目录位置
            List<Path> possibleRoots = new ArrayList<>();
            
            // 1. 从当前工作目录查找
            String currentDir = System.getProperty("user.dir");
            if (currentDir != null) {
                Path currentPath = Paths.get(currentDir);
                // 如果当前在 backend 目录，向上查找项目根目录
                if (currentPath.getFileName() != null && "backend".equals(currentPath.getFileName().toString())) {
                    possibleRoots.add(currentPath.getParent());
                } else {
                    // 检查当前目录是否是项目根目录（包含 backend 或 frontend 目录）
                    if (Files.exists(currentPath.resolve("backend")) || Files.exists(currentPath.resolve("frontend"))) {
                        possibleRoots.add(currentPath);
                    } else {
                        // 向上查找包含 backend 或 frontend 的目录
                        Path searchPath = currentPath;
                        for (int i = 0; i < 5 && searchPath != null; i++) {
                            if (Files.exists(searchPath.resolve("backend")) || Files.exists(searchPath.resolve("frontend"))) {
                                possibleRoots.add(searchPath);
                                break;
                            }
                            searchPath = searchPath.getParent();
                        }
                    }
                }
            }
            
            // 2. 从类路径推断项目根目录
            String classPath = System.getProperty("java.class.path", "");
            if (classPath.contains("backend")) {
                String[] paths = classPath.split(File.pathSeparator);
                for (String pathStr : paths) {
                    if (pathStr.contains("backend")) {
                        try {
                            Path path = Paths.get(pathStr);
                            // 找到 backend 目录
                            Path searchPath = path;
                            while (searchPath != null && searchPath.getFileName() != null) {
                                if ("backend".equals(searchPath.getFileName().toString())) {
                                    if (searchPath.getParent() != null) {
                                        possibleRoots.add(searchPath.getParent());
                                    }
                                    break;
                                }
                                searchPath = searchPath.getParent();
                            }
                        } catch (Exception e) {
                            // 忽略路径解析错误
                        }
                    }
                }
            }
            
            // 3. 尝试每个可能的根目录
            for (Path root : possibleRoots) {
                if (root == null) continue;
                
                try {
                    Path toolsDir = root.resolve(".tools");
                    Path ytDlpExe = toolsDir.resolve("yt-dlp.exe");
                    
                    if (Files.exists(ytDlpExe) && Files.isRegularFile(ytDlpExe)) {
                        String absolutePath = ytDlpExe.toAbsolutePath().toString();
                        log.info("Found yt-dlp.exe at: {}", absolutePath);
                        return absolutePath;
                    }
                } catch (Exception e) {
                    // 继续尝试下一个路径
                }
            }
        } catch (Exception e) {
            log.debug("Error finding .tools/yt-dlp.exe: {}", e.getMessage());
        }
        
        return null;
    }

    private String resolveFfmpegPath() {
        List<String> candidates = new ArrayList<>();

        if (ffmpegPath != null && !ffmpegPath.trim().isEmpty()) {
            candidates.add(ffmpegPath.trim());
        }

        String toolsPath = findToolsFfmpegPath();
        if (toolsPath != null) {
            candidates.add(toolsPath);
        }

        boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        candidates.addAll(isWindows ? Arrays.asList("ffmpeg.exe", "ffmpeg") : Arrays.asList("ffmpeg", "ffmpeg.exe"));

        for (String candidate : candidates) {
            if (tryGetFfmpegVersion(candidate) != null) {
                return candidate;
            }
        }

        return null;
    }

    private String findToolsFfmpegPath() {
        try {
            Path root = findProjectRoot();
            if (root == null) {
                return null;
            }

            List<Path> directCandidates = Arrays.asList(
                    root.resolve(".tools").resolve("ffmpeg").resolve("bin").resolve("ffmpeg.exe"),
                    root.resolve(".tools").resolve("ffmpeg.exe")
            );

            for (Path candidate : directCandidates) {
                if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath().toString();
                }
            }

            Path toolsDir = root.resolve(".tools");
            if (!Files.exists(toolsDir)) {
                return null;
            }

            try (var paths = Files.find(toolsDir, 5, (path, attrs) ->
                    attrs.isRegularFile() && "ffmpeg.exe".equalsIgnoreCase(path.getFileName().toString()))) {
                return paths
                        .sorted(Comparator.comparing(path -> path.toAbsolutePath().toString()))
                        .map(path -> path.toAbsolutePath().toString())
                        .findFirst()
                        .orElse(null);
            }
        } catch (Exception e) {
            log.debug("Error finding project ffmpeg: {}", e.getMessage());
            return null;
        }
    }

    private Path findProjectRoot() {
        String currentDir = System.getProperty("user.dir");
        if (currentDir == null || currentDir.isBlank()) {
            return null;
        }

        Path searchPath = Paths.get(currentDir);
        for (int i = 0; i < 6 && searchPath != null; i++) {
            if (Files.exists(searchPath.resolve("backend")) || Files.exists(searchPath.resolve("frontend"))) {
                return searchPath;
            }
            searchPath = searchPath.getParent();
        }
        return null;
    }

    private String resolveFfmpegLocation() {
        if (resolvedFfmpegPath == null) {
            return "";
        }

        try {
            Path path = Paths.get(resolvedFfmpegPath);
            if (Files.isRegularFile(path) && path.getParent() != null) {
                return path.getParent().toAbsolutePath().toString();
            }
        } catch (Exception e) {
            log.debug("Failed to resolve ffmpeg location from {}: {}", resolvedFfmpegPath, e.getMessage());
        }

        return resolvedFfmpegPath;
    }

    private String tryGetVersion(String command) {
        if (command == null || command.trim().isEmpty()) {
            return null;
        }

        try {
            Process process = new ProcessBuilder(command, "--version").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String version = reader.readLine();
                if (version != null && !version.isEmpty()) {
                    return version;
                }
            }
        } catch (Exception e) {
            // Ignore and try next candidate
        }
        return null;
    }

    private String tryGetFfmpegVersion(String command) {
        if (command == null || command.trim().isEmpty()) {
            return null;
        }

        try {
            Process process = new ProcessBuilder(command, "-version").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String version = reader.readLine();
                if (version != null && !version.isEmpty()) {
                    return version;
                }
            }
        } catch (Exception e) {
            // Ignore and try next candidate
        }
        return null;
    }

    public record DownloadDiagnostics(
            String ytDlpPath,
            String ytDlpVersion,
            String ffmpegPath,
            boolean ffmpegAvailable,
            boolean cookiesConfigured,
            String cookieSource,
            boolean forceIpv4,
            long timeoutMinutes,
            List<String> formatFallbacks
    ) {}
}
