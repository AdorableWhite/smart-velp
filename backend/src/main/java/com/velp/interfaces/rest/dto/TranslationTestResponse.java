package com.velp.interfaces.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TranslationTestResponse {
    private boolean ok;
    private String message;
    private String translatedText;
    private long elapsedMs;
    private java.util.List<String> providerChain;
    private java.util.Map<String, ?> providerHealth;
}
