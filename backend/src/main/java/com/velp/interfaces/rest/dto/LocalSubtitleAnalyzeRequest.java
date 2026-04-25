package com.velp.interfaces.rest.dto;

import lombok.Data;

@Data
public class LocalSubtitleAnalyzeRequest {
    private String title;
    private String subtitleContent;
    private String subtitleFormat;
    private String sourceLang;
    private String targetLang;
    private TranslationProfileRequest translationProfile;
}
