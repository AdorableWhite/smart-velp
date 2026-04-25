package com.velp.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SubtitleLine {
    private double startTime;
    private double endTime;
    private String en;
    private String cn;
    private String sourceText;
    private String targetText;
    private String sourceLang;
    private String targetLang;

    public String getEffectiveSourceText() {
        return sourceText != null && !sourceText.isBlank() ? sourceText : en;
    }

    public String getEffectiveTargetText() {
        return targetText != null && !targetText.isBlank() ? targetText : cn;
    }

    public void setSourcePayload(String text, String lang) {
        this.sourceText = text;
        this.sourceLang = lang;
        this.en = text;
    }

    public void setTargetPayload(String text, String lang) {
        this.targetText = text;
        this.targetLang = lang;
        this.cn = text;
    }
}
