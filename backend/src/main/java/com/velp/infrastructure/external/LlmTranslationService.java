package com.velp.infrastructure.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.velp.common.constants.AppConstants;
import com.velp.domain.model.SubtitleLine;
import com.velp.domain.service.TranslationOptions;
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

@Service(AppConstants.Translation.SERVICE_LLM)
public class LlmTranslationService implements TranslationService {

    @Value("${velp.llm.enabled:false}")
    private boolean enabled;

    @Value("${velp.llm.api-key:}")
    private String apiKey;

    @Value("${velp.llm.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${velp.llm.model:deepseek-chat}")
    private String model;

    @Value("${velp.llm.request-timeout-seconds:20}")
    private int requestTimeoutSeconds;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TranslationResponseParser responseParser;

    public LlmTranslationService(ObjectMapper objectMapper, TranslationResponseParser responseParser) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
        this.responseParser = responseParser;
    }

    @Override
    public void translate(List<SubtitleLine> subtitles, TranslationOptions options, java.util.function.Consumer<Integer> progressCallback) {
        String effectiveApiKey = getEffectiveValue(options == null ? null : options.getApiKey(), apiKey);
        String effectiveBaseUrl = getEffectiveValue(options == null ? null : options.getBaseUrl(), baseUrl);
        String effectiveModel = getEffectiveValue(options == null ? null : options.getModel(), model);
        String sourceLang = getEffectiveValue(options == null ? null : options.getSourceLang(), "en");
        String targetLang = getEffectiveValue(options == null ? null : options.getTargetLang(), "zh-CN");

        boolean runtimeConfigured = effectiveApiKey != null && !effectiveApiKey.isEmpty()
                && effectiveBaseUrl != null && !effectiveBaseUrl.isEmpty();
        if ((!enabled && !runtimeConfigured) || effectiveApiKey == null || effectiveApiKey.isEmpty() || effectiveApiKey.startsWith("sk-your") || effectiveBaseUrl == null || effectiveBaseUrl.isEmpty()) {
            throw new IllegalStateException("LLM provider disabled or missing configuration");
        }

        List<SubtitleLine> linesToTranslate = subtitles.stream()
                .filter(s -> (s.getEffectiveTargetText() == null || s.getEffectiveTargetText().isEmpty())
                        && s.getEffectiveSourceText() != null
                        && !s.getEffectiveSourceText().isEmpty())
                .collect(Collectors.toList());

        if (linesToTranslate.isEmpty()) {
            if (progressCallback != null) {
                progressCallback.accept(100);
            }
            return;
        }

        try {
            translateBatch(linesToTranslate, effectiveApiKey, effectiveBaseUrl, effectiveModel, sourceLang, targetLang);
            if (progressCallback != null) {
                progressCallback.accept(100);
            }
        } catch (Exception e) {
            throw new RuntimeException("AI Translation failed: " + e.getMessage());
        }
    }

    private void translateBatch(List<SubtitleLine> batch, String effectiveApiKey, String effectiveBaseUrl, String effectiveModel, String sourceLang, String targetLang) throws Exception {
        List<String> englishLines = batch.stream()
                .map(SubtitleLine::getEffectiveSourceText)
                .collect(Collectors.toList());
        String inputJson = objectMapper.writeValueAsString(englishLines);

        String systemPrompt = "You are a professional subtitle translator. Translate subtitle lines from " + sourceLang + " to " + targetLang + ". " +
                "Output ONLY a JSON array of strings with the same length as the input array. No markdown, no explanation.";

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", effectiveModel);
        requestBody.put("temperature", 0.3);

        ArrayNode messages = requestBody.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", inputJson);

        String requestJson = objectMapper.writeValueAsString(requestBody);

        String apiUrl = effectiveBaseUrl + (effectiveBaseUrl.endsWith("/") ? "chat/completions" : "/chat/completions");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + effectiveApiKey)
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").get(0).path("message").path("content").asText();
            List<String> translations = responseParser.parseAndNormalize(content, batch.size(), AppConstants.Translation.PROVIDER_LLM);
            for (int j = 0; j < translations.size(); j++) {
                    String translated = translations.get(j);
                    batch.get(j).setTargetPayload(translated == null ? "" : translated.trim(), targetLang);
                }
        } else {
            throw new Exception("HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    private String getEffectiveValue(String runtimeValue, String fallbackValue) {
        return runtimeValue != null && !runtimeValue.isBlank() ? runtimeValue : fallbackValue;
    }
}
