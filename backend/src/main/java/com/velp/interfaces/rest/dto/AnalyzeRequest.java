package com.velp.interfaces.rest.dto;

import lombok.Data;

@Data
public class AnalyzeRequest {
    private String url;
    private String sourceLang;
    private String targetLang;
    private TranslationProfileRequest translationProfile;
}
