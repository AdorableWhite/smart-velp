package com.velp.infrastructure.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class TranslationResponseParser {

    private final ObjectMapper objectMapper;

    public TranslationResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<String> parseAndNormalize(String rawText, int expectedSize, String provider) {
        if (expectedSize <= 0) {
            return Collections.emptyList();
        }
        if (rawText == null || rawText.trim().isEmpty()) {
            log.warn("Empty translation response from provider {}", provider);
            return padToSize(Collections.emptyList(), expectedSize, provider, "empty");
        }

        String cleaned = stripCodeFences(rawText.trim());
        List<String> parsed = tryParseJsonArray(cleaned);
        String mode = "json";

        if (parsed == null) {
            String extracted = extractJsonArray(cleaned);
            parsed = extracted == null ? null : tryParseJsonArray(extracted);
            mode = parsed == null ? "fallback_lines" : "json_extracted";
        }

        if (parsed == null) {
            parsed = splitByLines(cleaned);
        }

        if (!"json".equals(mode) && !"json_extracted".equals(mode)) {
            log.warn("Translation response fallback parsing used for provider {}", provider);
        }

        return padToSize(parsed, expectedSize, provider, mode);
    }

    private List<String> tryParseJsonArray(String content) {
        try {
            JsonNode node = objectMapper.readTree(content);
            if (!node.isArray()) {
                return null;
            }
            List<String> results = new ArrayList<>();
            for (JsonNode item : node) {
                results.add(item.isNull() ? "" : item.asText());
            }
            return results;
        } catch (Exception e) {
            return null;
        }
    }

    private String stripCodeFences(String text) {
        if (!text.startsWith("```")) {
            return text;
        }
        return text.replaceAll("```(json)?", "").replaceAll("```", "").trim();
    }

    private String extractJsonArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    private List<String> splitByLines(String text) {
        String[] lines = text.split("\\r?\\n", -1);
        List<String> results = new ArrayList<>();
        for (String line : lines) {
            results.add(line == null ? "" : line.trim());
        }
        return results;
    }

    private List<String> padToSize(List<String> input, int expectedSize, String provider, String mode) {
        List<String> normalized = new ArrayList<>(expectedSize);
        if (input != null) {
            normalized.addAll(input);
        }
        if (normalized.size() < expectedSize) {
            log.warn("Translation size mismatch for provider {} (mode: {}), expected {}, got {}. Padding.",
                    provider, mode, expectedSize, normalized.size());
            while (normalized.size() < expectedSize) {
                normalized.add("");
            }
        } else if (normalized.size() > expectedSize) {
            log.warn("Translation size mismatch for provider {} (mode: {}), expected {}, got {}. Truncating.",
                    provider, mode, expectedSize, normalized.size());
            normalized = normalized.subList(0, expectedSize);
        }
        return normalized;
    }
}
