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
        List<SubtitleLine> lines = new ArrayList<>();
        if (!vttFile.exists()) {
            return lines;
        }

        // Use UTF-8 explicitly
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(vttFile), StandardCharsets.UTF_8))) {
            String line;
            SubtitleLine currentLine = null;
            StringBuilder textBuffer = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                // Remove BOM if present
                if (line.startsWith(AppConstants.Subtitle.BOM)) {
                    line = line.substring(1);
                }
                line = line.trim();
                
                if (line.equals(AppConstants.Subtitle.WEBVTT_HEADER) || line.isEmpty()) {
                    continue;
                }

                if (line.contains(AppConstants.Subtitle.TIME_SEPARATOR)) {
                    if (currentLine != null) {
                        currentLine.setEn(textBuffer.toString().trim());
                        lines.add(currentLine);
                        textBuffer.setLength(0);
                    }

                    String[] times = line.split(AppConstants.Subtitle.TIME_SEPARATOR);
                    if (times.length == 2) {
                        currentLine = new SubtitleLine();
                        currentLine.setStartTime(parseTime(times[0].trim()));
                        currentLine.setEndTime(parseTime(times[1].trim()));
                    }
                } else if (currentLine != null) {
                    if (textBuffer.length() > 0) {
                        textBuffer.append(" ");
                    }
                    textBuffer.append(line);
                }
            }
            
            if (currentLine != null) {
                currentLine.setEn(textBuffer.toString().trim());
                lines.add(currentLine);
            }

        } catch (IOException e) {
            log.error("Failed to parse VTT file: {}", vttFile.getAbsolutePath(), e);
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
                    en.setCn(cn.getEn()); 
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
