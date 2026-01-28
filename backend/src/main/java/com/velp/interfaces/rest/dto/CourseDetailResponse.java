package com.velp.interfaces.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CourseDetailResponse {
    private String title;
    private String videoUrl;
    private List<SubtitleLineDto> subtitles;
}
