package com.velp.interfaces.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SubtitleLineDto {
    private double startTime;
    private double endTime;
    private String en;
    private String cn;
    private String sourceText;
    private String targetText;
    private String sourceLang;
    private String targetLang;
}
