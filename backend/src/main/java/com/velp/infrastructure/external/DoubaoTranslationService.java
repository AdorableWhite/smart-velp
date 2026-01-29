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

    @Value("${velp.llm.request-timeout-seconds:20}")
    private int requestTimeoutSeconds;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TranslationResponseParser responseParser;

    public DoubaoTranslationService(ObjectMapper objectMapper, TranslationResponseParser responseParser) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.objectMapper = objectMapper;
        this.responseParser = responseParser;
    }

    @Override
    public void translate(List<SubtitleLine> subtitles) {
        translate(subtitles, null);
    }

    @Override
    public void translate(List<SubtitleLine> subtitles, java.util.function.Consumer<Integer> progressCallback) {
        if (!enabled || apiKey == null || apiKey.isEmpty() || baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalStateException("Doubao provider disabled or missing configuration");
        }

        List<SubtitleLine> linesToTranslate = subtitles.stream()
                .filter(s -> (s.getCn() == null || s.getCn().isEmpty()) && s.getEn() != null)
                .collect(Collectors.toList());

        if (linesToTranslate.isEmpty()) {
            if (progressCallback != null) progressCallback.accept(100);
            return;
        }

        try {
            translateBatch(linesToTranslate);
            if (progressCallback != null) {
                progressCallback.accept(100);
            }
        } catch (Exception e) {
            log.error("Doubao translation failed", e);
            throw new RuntimeException("Doubao Translation error: " + e.getMessage());
        }
    }

    private void translateBatch(List<SubtitleLine> batch) throws Exception {
        List<String> englishLines = batch.stream()
                .map(SubtitleLine::getEn)
                .collect(Collectors.toList());
        String inputJson = objectMapper.writeValueAsString(englishLines);

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        
        ArrayNode inputArray = requestBody.putArray("input");
        ObjectNode userMessage = inputArray.addObject();
        userMessage.put("role", AppConstants.Translation.ROLE_USER);
        
        ArrayNode contentArray = userMessage.putArray("content");
        contentArray.addObject()
                .put("type", AppConstants.Translation.TYPE_INPUT_TEXT)
                .put("text", "Translate these English lines to Simplified Chinese. Output ONLY a JSON array of strings with the same length as the input array. No explanation, no markdown blocks. Input JSON array:\n" + inputJson);

        String requestJson = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            
            // Handle both standard OpenAI structure and Doubao-specific structure
            String resultText = "";
            if (root.has("choices") && root.get("choices").isArray() && root.get("choices").size() > 0) {
                // Standard OpenAI structure
                resultText = root.path("choices").path(0).path("message").path("content").asText();
            } else if (root.has("output") && root.get("output").isArray() && root.get("output").size() > 0) {
                // Doubao-specific structure (as seen in the error log)
                // The output array contains multiple items, we need the one with type "message"
                for (JsonNode outputItem : root.get("output")) {
                    if ("message".equals(outputItem.path("role").asText()) || "assistant".equals(outputItem.path("role").asText())) {
                        JsonNode responseContentArray = outputItem.path("content");
                        if (responseContentArray.isArray() && responseContentArray.size() > 0) {
                            resultText = responseContentArray.path(0).path("text").asText();
                            if (resultText == null || resultText.isEmpty()) {
                                resultText = responseContentArray.path(0).path("output_text").asText();
                            }
                            break;
                        }
                    }
                }
            }

            if (resultText != null && !resultText.isEmpty()) {
                List<String> translations = responseParser.parseAndNormalize(resultText, batch.size(), AppConstants.Translation.PROVIDER_DOUBAO);
                for (int j = 0; j < translations.size(); j++) {
                    String translated = translations.get(j);
                    batch.get(j).setCn(translated == null ? "" : translated.trim());
                }
            } else {
                throw new Exception("Unexpected Doubao response structure: " + response.body());
            }
        } else {
            throw new Exception("HTTP " + response.statusCode() + ": " + response.body());
        }
    }
}
