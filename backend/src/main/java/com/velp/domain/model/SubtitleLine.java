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
}
