package com.velp.domain.service;

import com.velp.domain.model.SubtitleLine;
import java.util.List;

public interface TranslationService {
    void translate(List<SubtitleLine> subtitles);
    default void translate(List<SubtitleLine> subtitles, java.util.function.Consumer<Integer> progressCallback) {
        translate(subtitles);
    }
}
