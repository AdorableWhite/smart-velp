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
        if (AppConstants.Translation.PROVIDER_DOUBAO.equalsIgnoreCase(provider)) {
            return context.getBean(AppConstants.Translation.SERVICE_DOUBAO, TranslationService.class);
        } else {
            // Default to DeepSeek
            return context.getBean(AppConstants.Translation.SERVICE_DEEPSEEK, TranslationService.class);
        }
    }
}
