package com.velp.infrastructure.factory;

import com.velp.common.constants.AppConstants;
import com.velp.domain.service.TranslationService;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class TranslationServiceFactory {

    private final ApplicationContext context;

    public TranslationServiceFactory(ApplicationContext context) {
        this.context = context;
    }

    public TranslationService getService(String provider) {
        if (provider == null || provider.trim().isEmpty()) {
            return null;
        }
        String normalized = provider.trim().toLowerCase();
        if (AppConstants.Translation.PROVIDER_DOUBAO.equalsIgnoreCase(normalized)) {
            return context.getBean(AppConstants.Translation.SERVICE_DOUBAO, TranslationService.class);
        }
        if (AppConstants.Translation.PROVIDER_DEEPSEEK.equalsIgnoreCase(normalized)) {
            return context.getBean(AppConstants.Translation.SERVICE_DEEPSEEK, TranslationService.class);
        }
        if (AppConstants.Translation.PROVIDER_OPENAI.equalsIgnoreCase(normalized)
                || AppConstants.Translation.PROVIDER_LLM.equalsIgnoreCase(normalized)) {
            return context.getBean(AppConstants.Translation.SERVICE_LLM, TranslationService.class);
        }
        return null;
    }
}
