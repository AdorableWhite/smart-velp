package com.velp.interfaces.rest;

import com.velp.application.TranslationManager;
import com.velp.domain.model.SubtitleLine;
import com.velp.domain.service.TranslationOptions;
import com.velp.interfaces.rest.dto.TranslationTestRequest;
import com.velp.interfaces.rest.dto.TranslationTestResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/translation")
public class TranslationController {

    private final TranslationManager translationManager;

    public TranslationController(TranslationManager translationManager) {
        this.translationManager = translationManager;
    }

    @PostMapping("/test")
    public TranslationTestResponse testService(@RequestBody TranslationTestRequest body) {
        long started = System.nanoTime();
        TranslationOptions options = buildOptions(body);
        List<String> providerChain = translationManager.getProviderChain(options);
        Map<String, TranslationManager.ProviderHealth> providerHealth = translationManager.getProviderHealth();

        try {
            List<SubtitleLine> samples = new ArrayList<>();
            SubtitleLine line = new SubtitleLine();
            line.setStartTime(0);
            line.setEndTime(1);
            line.setSourcePayload("Hello, this is a connection test.", normalize(body.getSourceLang(), "en"));
            line.setTargetPayload("", null);
            samples.add(line);

            translationManager.translate(samples, options);
            return new TranslationTestResponse(
                    true,
                    "Translation connectivity check passed",
                    samples.get(0).getEffectiveTargetText(),
                    elapsedMs(started),
                    providerChain,
                    translationManager.getProviderHealth()
            );
        } catch (Exception e) {
            providerHealth = translationManager.getProviderHealth();
            return new TranslationTestResponse(
                    false,
                    e.getMessage(),
                    "",
                    elapsedMs(started),
                    providerChain,
                    translationManager.getProviderHealth()
            );
        }
    }

    @GetMapping("/health")
    public Map<String, TranslationManager.ProviderHealth> getHealth() {
        return translationManager.getProviderHealth();
    }

    private TranslationOptions buildOptions(TranslationTestRequest body) {
        return TranslationOptions.builder()
                .provider(body.getProvider())
                .baseUrl(body.getBaseUrl())
                .model(body.getModel())
                .apiKey(body.getApiKey())
                .prompt(body.getPrompt())
                .sourceLang(normalize(body.getSourceLang(), "en"))
                .targetLang(normalize(body.getTargetLang(), "zh-CN"))
                .build();
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
