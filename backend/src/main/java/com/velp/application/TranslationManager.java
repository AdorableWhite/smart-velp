package com.velp.application;

import com.velp.common.constants.AppConstants;
import com.velp.domain.model.SubtitleLine;
import com.velp.domain.service.TranslationService;
import com.velp.infrastructure.factory.TranslationServiceFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@Primary
public class TranslationManager implements TranslationService {

    private final TranslationServiceFactory factory;
    private final String preferredProvider;

    public TranslationManager(TranslationServiceFactory factory, @Value("${velp.llm.preferred:deepseek}") String preferredProvider) {
        this.factory = factory;
        this.preferredProvider = preferredProvider;
    }

    @Override
    public void translate(List<SubtitleLine> subtitles) {
        translate(subtitles, null);
    }

    @Override
    public void translate(List<SubtitleLine> subtitles, java.util.function.Consumer<Integer> progressCallback) {
        TranslationService service = factory.getService(preferredProvider);
        
        log.info("Using translation provider: {}", preferredProvider);
        service.translate(subtitles, progressCallback);
    }
}
