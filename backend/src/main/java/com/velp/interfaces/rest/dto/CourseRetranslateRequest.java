package com.velp.interfaces.rest.dto;

import lombok.Data;

@Data
public class CourseRetranslateRequest {
    private String sourceLang;
    private String targetLang;
    private TranslationProfileRequest translationProfile;
}
