package com.velp.interfaces.rest.dto;

import lombok.Data;

@Data
public class TranslationProfileRequest {
    private String provider;
    private String baseUrl;
    private String model;
    private String apiKey;
}
