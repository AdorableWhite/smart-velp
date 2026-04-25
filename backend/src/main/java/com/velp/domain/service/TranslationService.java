package com.velp.domain.service;

import com.velp.domain.model.SubtitleLine;
import java.util.List;

public interface TranslationService {
    default void translate(List<SubtitleLine> subtitles) {
        translate(subtitles, TranslationOptions.defaults(), null);
    }

    default void translate(List<SubtitleLine> subtitles, java.util.function.Consumer<Integer> progressCallback) {
        translate(subtitles, TranslationOptions.defaults(), progressCallback);
    }

    default void translate(List<SubtitleLine> subtitles, TranslationOptions options) {
        translate(subtitles, options, null);
    }

    void translate(List<SubtitleLine> subtitles, TranslationOptions options, java.util.function.Consumer<Integer> progressCallback);
}
