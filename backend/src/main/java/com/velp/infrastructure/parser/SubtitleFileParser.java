package com.velp.infrastructure.parser;

import com.velp.common.constants.AppConstants;
import com.velp.domain.model.SubtitleLine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class SubtitleFileParser {

    public List<SubtitleLine> parseVtt(File vttFile) {
        if (!vttFile.exists()) {
            return new ArrayList<>();
        }

        // Use UTF-8 explicitly
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(vttFile), StandardCharsets.UTF_8))) {
            String content = reader.lines().reduce("", (left, right) -> left + "\n" + right);
            return parseContent(content, "vtt", "en");
        } catch (IOException e) {
            log.error("Failed to parse VTT file: {}", vttFile.getAbsolutePath(), e);
        }
        return new ArrayList<>();
    }

    public List<SubtitleLine> parseContent(String content, String format, String sourceLang) {
        List<SubtitleLine> lines = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return lines;
        }

        String normalized = content.replace("\r\n", "\n").replace("\r", "\n");
        String[] blocks = normalized.split("\n\\s*\n");

        for (String rawBlock : blocks) {
            if (rawBlock == null || rawBlock.isBlank()) {
                continue;
            }

            String block = rawBlock.startsWith(AppConstants.Subtitle.BOM)
                    ? rawBlock.substring(1)
                    : rawBlock;
            String[] rows = block.split("\n");
            String timingRow = null;
            StringBuilder textBuffer = new StringBuilder();

            for (String rawLine : rows) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.equals(AppConstants.Subtitle.WEBVTT_HEADER) || line.matches("^\\d+$")) {
                    continue;
                }

                if (line.contains(AppConstants.Subtitle.TIME_SEPARATOR)) {
                    timingRow = line;
                    continue;
                }

                if (textBuffer.length() > 0) {
                    textBuffer.append(" ");
                }
                textBuffer.append(line);
            }

            if (timingRow == null) {
                continue;
            }

            String[] times = timingRow.split(AppConstants.Subtitle.TIME_SEPARATOR);
            if (times.length != 2) {
                continue;
            }

            SubtitleLine subtitleLine = new SubtitleLine();
            subtitleLine.setStartTime(parseTime(times[0].trim()));
            subtitleLine.setEndTime(parseTime(times[1].trim()));
            subtitleLine.setSourcePayload(textBuffer.toString().trim(), sourceLang == null || sourceLang.isBlank() ? "en" : sourceLang);
            subtitleLine.setTargetPayload("", null);
            lines.add(subtitleLine);
        }

        return lines;
    }

    private double parseTime(String timeString) {
        try {
            String[] parts = timeString.split(":");
            if (parts.length == 3) {
                double hours = Double.parseDouble(parts[0]);
                double minutes = Double.parseDouble(parts[1]);
                double seconds = Double.parseDouble(parts[2]);
                return hours * 3600 + minutes * 60 + seconds;
            }
        } catch (Exception e) {
            log.warn("Failed to parse time string: {}", timeString);
        }
        return 0.0;
    }

    public List<SubtitleLine> mergeSubtitles(List<SubtitleLine> enSubs, List<SubtitleLine> cnSubs) {
        for (SubtitleLine en : enSubs) {
            for (SubtitleLine cn : cnSubs) {
                if (isOverlapping(en, cn)) {
                    en.setTargetPayload(cn.getEffectiveSourceText(), cn.getSourceLang() == null ? "zh-CN" : cn.getSourceLang());
                    break;
                }
            }
        }
        return enSubs;
    }

    private boolean isOverlapping(SubtitleLine s1, SubtitleLine s2) {
        double start = Math.max(s1.getStartTime(), s2.getStartTime());
        double end = Math.min(s1.getEndTime(), s2.getEndTime());
        return end > start;
    }
}
