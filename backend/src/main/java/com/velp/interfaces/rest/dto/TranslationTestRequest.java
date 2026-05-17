package com.velp.interfaces.rest.dto;

import lombok.Data;

@Data
public class TranslationTestRequest {
    private String provider;
    private String baseUrl;
    private String model;
    private String apiKey;
    private String prompt;
    private String sourceLang;
    private String targetLang;
}
