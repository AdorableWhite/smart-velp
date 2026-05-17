package com.velp.interfaces.rest;

import com.velp.domain.model.SubtitleLine;
import com.velp.domain.service.TranslationOptions;
import com.velp.domain.service.TranslationService;
import com.velp.infrastructure.factory.TranslationServiceFactory;
import com.velp.interfaces.rest.dto.TranslationTestRequest;
import com.velp.interfaces.rest.dto.TranslationTestResponse;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/translation")
public class TranslationController {

    private final TranslationServiceFactory translationServiceFactory;

    public TranslationController(TranslationServiceFactory translationServiceFactory) {
        this.translationServiceFactory = translationServiceFactory;
    }

    @PostMapping("/test")
    public TranslationTestResponse testService(@RequestBody TranslationTestRequest body) {
        long started = System.nanoTime();
        String provider = body.getProvider();
        TranslationService service = translationServiceFactory.getService(provider);
        if (service == null) {
            return new TranslationTestResponse(false, "不支持的翻译服务类型", "", elapsedMs(started));
        }

        try {
            List<SubtitleLine> samples = new ArrayList<>();
            SubtitleLine line = new SubtitleLine();
            line.setStartTime(0);
            line.setEndTime(1);
            line.setSourcePayload("Hello, this is a connection test.", normalize(body.getSourceLang(), "en"));
            line.setTargetPayload("", null);
            samples.add(line);

            TranslationOptions options = TranslationOptions.builder()
                    .provider(provider)
                    .baseUrl(body.getBaseUrl())
                    .model(body.getModel())
                    .apiKey(body.getApiKey())
                    .prompt(body.getPrompt())
                    .sourceLang(normalize(body.getSourceLang(), "en"))
                    .targetLang(normalize(body.getTargetLang(), "zh-CN"))
                    .build();

            service.translate(samples, options);
            return new TranslationTestResponse(
                    true,
                    "连通性检测通过",
                    samples.get(0).getEffectiveTargetText(),
                    elapsedMs(started)
            );
        } catch (Exception e) {
            return new TranslationTestResponse(false, e.getMessage(), "", elapsedMs(started));
        }
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
