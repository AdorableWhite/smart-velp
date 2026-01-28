package com.velp.infrastructure.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.velp.common.constants.AppConstants;
import com.velp.domain.model.SubtitleLine;
import com.velp.domain.service.TranslationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service(AppConstants.Translation.SERVICE_DEEPSEEK)
public class DeepSeekTranslationService implements TranslationService {

    @Value("${velp.llm.deepseek.enabled:false}")
    private boolean enabled;

    @Value("${velp.llm.deepseek.api-key:}")
    private String apiKey;

    @Value("${velp.llm.deepseek.base-url:}")
    private String baseUrl;

    @Value("${velp.llm.deepseek.model:}")
    private String model;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DeepSeekTranslationService(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public void translate(List<SubtitleLine> subtitles) {
        translate(subtitles, null);
    }

    @Override
    public void translate(List<SubtitleLine> subtitles, java.util.function.Consumer<Integer> progressCallback) {
        if (!enabled || apiKey == null || apiKey.isEmpty()) return;

        List<SubtitleLine> linesToTranslate = subtitles.stream()
                .filter(s -> (s.getCn() == null || s.getCn().isEmpty()) && s.getEn() != null)
                .collect(Collectors.toList());

        if (linesToTranslate.isEmpty()) {
            if (progressCallback != null) progressCallback.accept(100);
            return;
        }

        int batchSize = 20;
        for (int i = 0; i < linesToTranslate.size(); i += batchSize) {
            int end = Math.min(i + batchSize, linesToTranslate.size());
            List<SubtitleLine> batch = linesToTranslate.subList(i, end);
            try {
                translateBatch(batch);
                if (progressCallback != null) {
                    int progress = (int) (((double) end / linesToTranslate.size()) * 100);
                    progressCallback.accept(progress);
                }
            } catch (Exception e) {
                log.error("DeepSeek batch translation failed", e);
                throw new RuntimeException("DeepSeek Translation error: " + e.getMessage());
            }
        }
    }

    private void translateBatch(List<SubtitleLine> batch) throws Exception {
        String contentToTranslate = batch.stream()
                .map(SubtitleLine::getEn)
                .collect(Collectors.joining("\n"));

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        
        ArrayNode messages = requestBody.putArray("messages");
        messages.addObject().put("role", AppConstants.Translation.ROLE_SYSTEM).put("content", "You are a professional subtitle translator. Translate English to Chinese. One line per line.");
        messages.addObject().put("role", AppConstants.Translation.ROLE_USER).put("content", contentToTranslate);

        String requestJson = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                String content = choices.path(0).path("message").path("content").asText();
                String[] translatedLines = content.split("\n");
                int loopLimit = Math.min(batch.size(), translatedLines.length);
                for (int j = 0; j < loopLimit; j++) {
                    batch.get(j).setCn(translatedLines[j].trim());
                }
            } else {
                throw new Exception("Unexpected DeepSeek response structure: " + response.body());
            }
        } else {
            throw new Exception("HTTP " + response.statusCode() + ": " + response.body());
        }
    }
}
