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
@Service(AppConstants.Translation.SERVICE_DOUBAO)
public class DoubaoTranslationService implements TranslationService {

    @Value("${velp.llm.doubao.enabled:false}")
    private boolean enabled;

    @Value("${velp.llm.doubao.api-key:}")
    private String apiKey;

    @Value("${velp.llm.doubao.base-url:}")
    private String baseUrl;

    @Value("${velp.llm.doubao.model:}")
    private String model;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DoubaoTranslationService(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
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

        int batchSize = 15;
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
                log.error("Doubao batch translation failed", e);
                throw new RuntimeException("Doubao Translation error: " + e.getMessage());
            }
        }
    }

    private void translateBatch(List<SubtitleLine> batch) throws Exception {
        String contentToTranslate = batch.stream()
                .map(SubtitleLine::getEn)
                .collect(Collectors.joining("\n"));

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        
        ArrayNode inputArray = requestBody.putArray("input");
        ObjectNode userMessage = inputArray.addObject();
        userMessage.put("role", AppConstants.Translation.ROLE_USER);
        
        ArrayNode contentArray = userMessage.putArray("content");
        contentArray.addObject()
                .put("type", AppConstants.Translation.TYPE_INPUT_TEXT)
                .put("text", "Translate these English lines to Chinese. One line per sentence. No extra text:\n" + contentToTranslate);

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
                String resultText = choices.path(0).path("message").path("content").asText();
                
                String[] translatedLines = resultText.split("\n");
                int limit = Math.min(batch.size(), translatedLines.length);
                for (int j = 0; j < limit; j++) {
                    batch.get(j).setCn(translatedLines[j].trim());
                }
            } else {
                throw new Exception("Unexpected Doubao response structure: " + response.body());
            }
        } else {
            throw new Exception("HTTP " + response.statusCode() + ": " + response.body());
        }
    }
}
