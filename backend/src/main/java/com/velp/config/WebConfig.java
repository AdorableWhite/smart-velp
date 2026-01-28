package com.velp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${velp.storage.path:downloads}")
    private String storagePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absPath = new File(storagePath).getAbsolutePath();
        if (!absPath.endsWith(File.separator)) {
            absPath += File.separator;
        }
        
        registry.addResourceHandler("/downloads/**")
                .addResourceLocations("file:" + absPath);
    }
}
