package com.velp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${velp.storage.path:downloads}")
    private String storagePath;

    @Value("${velp.cors.allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absPath = new File(storagePath).getAbsolutePath();
        if (!absPath.endsWith(File.separator)) {
            absPath += File.separator;
        }
        
        registry.addResourceHandler("/downloads/**")
                .addResourceLocations("file:" + absPath);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 如果配置了允许的源，使用配置的值；否则允许所有源（开发环境）
        if (allowedOrigins != null && !allowedOrigins.trim().isEmpty()) {
            String[] origins = allowedOrigins.split(",");
            registry.addMapping("/api/**")
                    .allowedOrigins(origins)
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);
            
            // 同时也为静态资源（视频文件）配置 CORS
            registry.addMapping("/downloads/**")
                    .allowedOrigins(origins)
                    .allowedMethods("GET")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);
        } else {
            // 开发环境：允许所有源
            registry.addMapping("/api/**")
                    .allowedOriginPatterns("*")
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);
            
            registry.addMapping("/downloads/**")
                    .allowedOriginPatterns("*")
                    .allowedMethods("GET")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);
        }
    }
}
