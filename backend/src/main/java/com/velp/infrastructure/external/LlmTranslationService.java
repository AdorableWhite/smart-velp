package com.velp.infrastructure.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.velp.domain.model.SubtitleLine;
import com.velp.domain.service.TranslationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LlmTranslationService implements TranslationService {

    @Value("${velp.llm.enabled:false}")
    private boolean enabled;

    @Value("${velp.llm.api-key:}")
    private String apiKey;

    @Value("${velp.llm.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${velp.llm.model:deepseek-chat}")
    private String model;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LlmTranslationService(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public void translate(List<SubtitleLine> subtitles) {
        if (!enabled || apiKey == null || apiKey.isEmpty() || apiKey.startsWith("sk-your")) {
            return;
        }

        List<SubtitleLine> linesToTranslate = subtitles.stream()
                .filter(s -> (s.getCn() == null || s.getCn().isEmpty()) && s.getEn() != null && !s.getEn().isEmpty())
                .collect(Collectors.toList());

        if (linesToTranslate.isEmpty()) {
            return;
        }

        int batchSize = 20;
        for (int i = 0; i < linesToTranslate.size(); i += batchSize) {
            int end = Math.min(i + batchSize, linesToTranslate.size());
            List<SubtitleLine> batch = linesToTranslate.subList(i, end);
            try {
                translateBatch(batch);
            } catch (Exception e) {
                throw new RuntimeException("AI Translation failed: " + e.getMessage());
            }
            
            try { Thread.sleep(300); } catch (InterruptedException e) {}
        }
    }

    private void translateBatch(List<SubtitleLine> batch) throws Exception {
        String contentToTranslate = batch.stream()
                .map(SubtitleLine::getEn)
                .collect(Collectors.joining("\n"));

        String systemPrompt = """
                You are a professional subtitle translator. 
                Translate the following English lines to Chinese (Simplified). 
                Output EXACTLY one line of Chinese for each line of English input.
                Do not output any line numbers, bullet points, or extra explanation.
                Just the translation.
                """;

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("temperature", 0.3);

        ArrayNode messages = requestBody.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", contentToTranslate);

        String requestJson = objectMapper.writeValueAsString(requestBody);

        String apiUrl = baseUrl + (baseUrl.endsWith("/") ? "chat/completions" : "/chat/completions");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").get(0).path("message").path("content").asText();
            
            String[] translatedLines = content.split("\n");
            int loopLimit = Math.min(batch.size(), translatedLines.length);
            for (int j = 0; j < loopLimit; j++) {
                String translated = translatedLines[j].trim();
                if (translated.isEmpty()) continue;
                batch.get(j).setCn(translated);
            }
        } else {
            throw new Exception("HTTP " + response.statusCode() + ": " + response.body());
        }
    }
}
