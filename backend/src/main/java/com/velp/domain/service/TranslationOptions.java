package com.velp.domain.service;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TranslationOptions {
    String sourceLang;
    String targetLang;
    String provider;
    String baseUrl;
    String model;
    String apiKey;
    String prompt;

    public static TranslationOptions defaults() {
        return TranslationOptions.builder()
                .sourceLang("en")
                .targetLang("zh-CN")
                .build();
    }

    public String normalizedProvider() {
        if (provider == null || provider.isBlank()) {
            return null;
        }

        String normalized = provider.trim().toLowerCase();
        if ("free".equals(normalized)) {
            return "doubao";
        }
        return normalized;
    }
}
