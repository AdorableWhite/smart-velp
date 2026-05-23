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
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SubtitleFileParser {
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern ENTITY_LRM_RLM = Pattern.compile("&(?:lrm|rlm);", Pattern.CASE_INSENSITIVE);

    public List<SubtitleLine> parseVtt(File vttFile) {
        return parseVtt(vttFile, "en");
    }

    public List<SubtitleLine> parseVtt(File vttFile, String sourceLang) {
        if (!vttFile.exists()) {
            return new ArrayList<>();
        }

        // Use UTF-8 explicitly
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(vttFile), StandardCharsets.UTF_8))) {
            String content = reader.lines().reduce("", (left, right) -> left + "\n" + right);
            return parseContent(content, "vtt", sourceLang);
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
            boolean skipBlock = false;

            for (String rawLine : rows) {
                String line = rawLine.trim();
                String upperLine = line.toUpperCase();
                if (line.isEmpty()
                        || upperLine.equals(AppConstants.Subtitle.WEBVTT_HEADER)
                        || upperLine.startsWith("NOTE")
                        || upperLine.startsWith("STYLE")
                        || upperLine.startsWith("REGION")
                        || line.matches("^\\d+$")) {
                    skipBlock = upperLine.startsWith("NOTE") || upperLine.startsWith("STYLE") || upperLine.startsWith("REGION");
                    continue;
                }

                if (line.contains(AppConstants.Subtitle.TIME_SEPARATOR)) {
                    timingRow = line;
                    continue;
                }

                if (textBuffer.length() > 0) {
                    textBuffer.append(" ");
                }
                textBuffer.append(cleanSubtitleText(line));
            }

            if (skipBlock || timingRow == null) {
                continue;
            }

            String[] times = timingRow.split(AppConstants.Subtitle.TIME_SEPARATOR);
            if (times.length != 2) {
                continue;
            }

            SubtitleLine subtitleLine = new SubtitleLine();
            subtitleLine.setStartTime(parseTime(extractTimestamp(times[0])));
            subtitleLine.setEndTime(parseTime(extractTimestamp(times[1])));
            String text = textBuffer.toString().replaceAll("\\s+", " ").trim();
            if (text.isBlank() || subtitleLine.getEndTime() <= subtitleLine.getStartTime()) {
                continue;
            }
            subtitleLine.setSourcePayload(text, sourceLang == null || sourceLang.isBlank() ? "en" : sourceLang);
            subtitleLine.setTargetPayload("", null);
            lines.add(subtitleLine);
        }

        return normalizeTimeline(lines);
    }

    private double parseTime(String timeString) {
        try {
            String[] parts = timeString.replace(',', '.').split(":");
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

    private String extractTimestamp(String rawTimestamp) {
        String cleaned = rawTimestamp == null ? "" : rawTimestamp.trim().replace(',', '.');
        int firstSpace = cleaned.indexOf(' ');
        return firstSpace > 0 ? cleaned.substring(0, firstSpace) : cleaned;
    }

    private String cleanSubtitleText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return ENTITY_LRM_RLM.matcher(TAG_PATTERN.matcher(text).replaceAll("")).replaceAll("");
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

    private List<SubtitleLine> normalizeTimeline(List<SubtitleLine> lines) {
        List<SubtitleLine> normalized = new ArrayList<>();
        lines.stream()
                .sorted(Comparator.comparingDouble(SubtitleLine::getStartTime)
                        .thenComparingDouble(SubtitleLine::getEndTime))
                .forEach(line -> {
                    if (line.getEndTime() <= line.getStartTime()) {
                        return;
                    }

                    String text = line.getEffectiveSourceText();
                    if (text == null || text.isBlank()) {
                        return;
                    }

                    if (!normalized.isEmpty()) {
                        SubtitleLine previous = normalized.get(normalized.size() - 1);
                        if (isDuplicateCue(previous, line)) {
                            return;
                        }
                        if (line.getStartTime() < previous.getEndTime()) {
                            line.setStartTime(Math.min(line.getEndTime(), previous.getEndTime()));
                        }
                    }

                    if (line.getEndTime() > line.getStartTime()) {
                        normalized.add(line);
                    }
                });
        return normalized;
    }

    private boolean isDuplicateCue(SubtitleLine left, SubtitleLine right) {
        return Math.abs(left.getStartTime() - right.getStartTime()) < 0.05
                && Math.abs(left.getEndTime() - right.getEndTime()) < 0.05
                && normalizeText(left.getEffectiveSourceText()).equals(normalizeText(right.getEffectiveSourceText()));
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }
}
