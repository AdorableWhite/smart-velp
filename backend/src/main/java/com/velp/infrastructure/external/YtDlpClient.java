package com.velp.infrastructure.external;

import com.velp.common.constants.AppConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class YtDlpClient {

    @Value("${velp.ytdlp.path:yt-dlp}")
    private String ytDlpPath;

    public void downloadVideo(String url, String outputTemplate, java.util.function.Consumer<Integer> progressCallback) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(ytDlpPath);
        command.add("-f");
        command.add(AppConstants.YtDlp.FORMAT_BEST);
        command.add("--write-sub");
        command.add("--write-auto-sub");
        command.add("--sub-lang");
        command.add(AppConstants.YtDlp.SUB_LANGS);
        command.add("--ignore-errors");
        command.add("--no-playlist");
        command.add("--no-cache-dir");
        command.add("--no-check-certificates");
        command.add("--force-overwrites");
        command.add("--extractor-args");
        command.add(AppConstants.YtDlp.EXTRACTOR_ARGS_YT);
        command.add("--user-agent");
        command.add("Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1");
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
}
