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
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class YtDlpClient {

    @Value("${velp.ytdlp.path:yt-dlp}")
    private String ytDlpPath;

    private String resolvedYtDlpPath;

    @PostConstruct
    public void init() {
        resolvedYtDlpPath = resolveYtDlpPath();
        String version = tryGetVersion(resolvedYtDlpPath);
        if (version != null) {
            log.info("Initialized YtDlpClient using {} (version: {})", resolvedYtDlpPath, version);
        } else {
            log.warn("Failed to initialize yt-dlp. Tried: {}", resolvedYtDlpPath);
        }
    }

    public String getVideoTitle(String url) {
        try {
            List<String> command = new ArrayList<>();
            command.add(resolvedYtDlpPath);
            command.add("--get-title");
            command.add("--no-playlist");
            command.add("--ignore-errors");
            command.add("--no-check-certificates");
            command.add("--extractor-args");
            command.add(AppConstants.YtDlp.EXTRACTOR_ARGS_YT);
            command.add(url);

            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String title = reader.readLine();
                if (title != null && !title.isEmpty()) {
                    return title;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get video title for {}: {}", url, e.getMessage());
        }
        return "Unknown Video";
    }

    public void downloadVideo(String url, String outputTemplate, java.util.function.Consumer<Integer> progressCallback) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(resolvedYtDlpPath);
        command.add("-f");
        command.add(AppConstants.YtDlp.FORMAT_BEST);
        command.add("--write-sub");
        command.add("--write-auto-sub");
        command.add("--sub-lang");
        command.add(AppConstants.YtDlp.SUB_LANGS);
        command.add("--geo-bypass");
        command.add("--ignore-errors");
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

        List<String> outputLines = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputLines.add(line);
                
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
                        // Ignore parsing errors
                    }
                }
            }
        }

        boolean finished = process.waitFor(10, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("yt-dlp timed out");
        }
        
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            String fullOutput = String.join("\n", outputLines);
            log.error("yt-dlp failed. Output:\n{}", fullOutput);
            throw new RuntimeException("yt-dlp exited with code " + exitCode + ". See logs for output.");
        }
        progressCallback.accept(80);
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
}
