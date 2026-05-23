package com.velp.application;

import com.velp.domain.model.SubtitleLine;
import com.velp.domain.service.TranslationOptions;
import com.velp.domain.service.TranslationService;
import com.velp.infrastructure.factory.TranslationServiceFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@Primary
public class TranslationManager implements TranslationService {

    private final TranslationServiceFactory factory;
    private final String preferredProvider;
    private final String fallbackProviders;

    @Value("${velp.llm.batch-size:30}")
    private int batchSize;

    @Value("${velp.llm.retry.max-attempts:2}")
    private int maxAttempts;

    @Value("${velp.llm.circuit-breaker.threshold:3}")
    private int circuitBreakerThreshold;

    @Value("${velp.llm.circuit-breaker.cooldown-seconds:60}")
    private int circuitBreakerCooldownSeconds;

    private final Map<String, FailureState> failureStates = new ConcurrentHashMap<>();
    private final Map<String, String> translationCache = new ConcurrentHashMap<>();

    public TranslationManager(
            TranslationServiceFactory factory,
            @Value("${velp.llm.preferred:deepseek}") String preferredProvider,
            @Value("${velp.llm.fallback-providers:}") String fallbackProviders
    ) {
        this.factory = factory;
        this.preferredProvider = preferredProvider;
        this.fallbackProviders = fallbackProviders;
    }

    @Override
    public void translate(List<SubtitleLine> subtitles, TranslationOptions options, java.util.function.Consumer<Integer> progressCallback) {
        if (subtitles == null || subtitles.isEmpty()) {
            if (progressCallback != null) progressCallback.accept(100);
            return;
        }

        TranslationOptions runtimeOptions = options == null ? TranslationOptions.defaults() : options;

        if (runtimeOptions.getTargetLang() != null
                && runtimeOptions.getSourceLang() != null
                && runtimeOptions.getTargetLang().equalsIgnoreCase(runtimeOptions.getSourceLang())) {
            subtitles.forEach(line -> {
                if ((line.getEffectiveTargetText() == null || line.getEffectiveTargetText().isEmpty())
                        && line.getEffectiveSourceText() != null) {
                    line.setTargetPayload(line.getEffectiveSourceText(), runtimeOptions.getTargetLang());
                }
            });
            if (progressCallback != null) progressCallback.accept(100);
            return;
        }

        List<SubtitleLine> candidates = subtitles.stream()
                .filter(s -> (s.getEffectiveTargetText() == null || s.getEffectiveTargetText().isEmpty())
                        && s.getEffectiveSourceText() != null
                        && !s.getEffectiveSourceText().isEmpty())
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            if (progressCallback != null) progressCallback.accept(100);
            return;
        }

        int totalToTranslate = candidates.size();
        applyCache(candidates, runtimeOptions);
        candidates = candidates.stream()
                .filter(s -> (s.getEffectiveTargetText() == null || s.getEffectiveTargetText().isEmpty())
                        && s.getEffectiveSourceText() != null
                        && !s.getEffectiveSourceText().isEmpty())
                .collect(Collectors.toList());

        int remaining = candidates.size();
        int translatedCount = totalToTranslate - remaining;
        if (progressCallback != null && translatedCount > 0) {
            progressCallback.accept(translatedCount * 100 / totalToTranslate);
        }

        if (remaining <= 0) {
            if (progressCallback != null) progressCallback.accept(100);
            return;
        }

        List<String> providerChain = getProviderChain(runtimeOptions);
        if (providerChain.isEmpty()) {
            throw new RuntimeException("No translation providers configured");
        }

        int safeBatchSize = Math.max(1, Math.min(batchSize, 100));
        for (int i = 0; i < candidates.size(); i += safeBatchSize) {
            int end = Math.min(i + safeBatchSize, candidates.size());
            List<SubtitleLine> batch = candidates.subList(i, end);
            if (batch.isEmpty()) {
                continue;
            }
            translateBatchWithFallback(batch, providerChain, runtimeOptions);
            updateCache(batch, runtimeOptions);
            translatedCount += batch.size();
            if (progressCallback != null) {
                progressCallback.accept(Math.min(100, translatedCount * 100 / totalToTranslate));
            }
        }
    }

    public List<String> getProviderChain(TranslationOptions options) {
        List<String> providers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String runtimePreferred = options == null ? null : options.normalizedProvider();
        if (runtimePreferred != null && !runtimePreferred.isBlank()) {
            if (seen.add(runtimePreferred.toLowerCase())) {
                providers.add(runtimePreferred);
            }
            if (hasExplicitRuntimeEndpoint(options)) {
                return providers;
            }
        }
        if (preferredProvider != null && !preferredProvider.trim().isEmpty()) {
            String normalized = preferredProvider.trim();
            if (seen.add(normalized.toLowerCase())) {
                providers.add(normalized);
            }
        }
        if (fallbackProviders != null && !fallbackProviders.trim().isEmpty()) {
            String[] parts = fallbackProviders.split(",");
            for (String part : parts) {
                if (part == null || part.trim().isEmpty()) continue;
                String normalized = part.trim();
                if (seen.add(normalized.toLowerCase())) {
                    providers.add(normalized);
                }
            }
        }
        return providers;
    }

    private boolean hasExplicitRuntimeEndpoint(TranslationOptions options) {
        if (options == null) {
            return false;
        }
        if ("free".equalsIgnoreCase(options.getProvider())) {
            return false;
        }
        return (options.getBaseUrl() != null && !options.getBaseUrl().isBlank())
                || (options.getApiKey() != null && !options.getApiKey().isBlank())
                || (options.getModel() != null && !options.getModel().isBlank());
    }

    private void translateBatchWithFallback(List<SubtitleLine> batch, List<String> providerChain, TranslationOptions options) {
        RuntimeException lastError = null;
        List<String> providerErrors = new ArrayList<>();
        for (String provider : providerChain) {
            if (isCircuitOpen(provider)) {
                log.warn("Provider {} is in cooldown, skipping", provider);
                providerErrors.add(provider + ": circuit open");
                continue;
            }
            TranslationService service = factory.getService(provider);
            if (service == null) {
                log.warn("Provider {} not available in factory, skipping", provider);
                providerErrors.add(provider + ": unsupported provider");
                continue;
            }
            for (int attempt = 1; attempt <= Math.max(1, maxAttempts); attempt++) {
                long start = System.nanoTime();
                try {
                    service.translate(batch, options);
                    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                    log.info("Provider {} translated batch size {} in {} ms (attempt {})", provider, batch.size(), elapsedMs, attempt);
                    recordSuccess(provider);
                    return;
                } catch (IllegalStateException e) {
                    log.warn("Provider {} unavailable: {}", provider, e.getMessage());
                    providerErrors.add(provider + ": " + e.getMessage());
                    if (lastError == null) {
                        lastError = new RuntimeException(e);
                    }
                    recordUnavailable(provider);
                    break;
                } catch (Exception e) {
                    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                    log.warn("Provider {} failed attempt {} ({} ms): {}", provider, attempt, elapsedMs, e.getMessage());
                    providerErrors.add(provider + " attempt " + attempt + ": " + e.getMessage());
                    lastError = new RuntimeException(e);
                    if (attempt >= Math.max(1, maxAttempts)) {
                        recordFailure(provider);
                    }
                }
            }
        }
        String failureSummary = String.join(" | ", providerErrors);
        if (lastError != null) {
            throw new RuntimeException("翻译服务不可用：" + explainTranslationFailure(failureSummary, lastError), lastError);
        }
        throw new RuntimeException("翻译服务不可用：没有可用的翻译服务，请先在设置页配置并测试。");
    }

    private String explainTranslationFailure(String summary, Throwable error) {
        Throwable cursor = error;
        StringBuilder messages = new StringBuilder(summary == null ? "" : summary);
        while (cursor != null) {
            if (cursor.getMessage() != null && !cursor.getMessage().isBlank()) {
                if (messages.length() > 0) {
                    messages.append(" | ");
                }
                messages.append(cursor.getMessage());
            }
            cursor = cursor.getCause();
        }

        String raw = messages.toString();
        String lower = raw.toLowerCase();
        if (lower.contains("setlimitexceeded") || lower.contains("too many requests") || lower.contains("http 429")) {
            return "服务额度已用尽或触发限流，请更换可用服务、调整服务端额度，或填入自己的 API Key。";
        }
        if (lower.contains("authentication") || lower.contains("invalid") || lower.contains("http 401") || lower.contains("api key")) {
            return "API Key 无效或缺失，请在设置页检查对应服务的 API Key。";
        }
        if (lower.contains("disabled") || lower.contains("missing configuration") || lower.contains("unavailable")) {
            return "服务未启用或缺少配置，请在设置页保存服务配置后再测试。";
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "请求超时，请稍后重试或切换网络/服务。";
        }
        return raw.isBlank() ? "未知错误，请查看后端日志。" : raw;
    }

    private int applyCache(List<SubtitleLine> candidates, TranslationOptions options) {
        int hits = 0;
        for (SubtitleLine line : candidates) {
            String sourceText = line.getEffectiveSourceText();
            if (sourceText == null || sourceText.isEmpty()) {
                continue;
            }
            String key = hashKey(sourceText, options.getTargetLang(), options.getPrompt());
            String cached = translationCache.get(key);
            if (cached != null && !cached.isEmpty()) {
                line.setTargetPayload(cached, options.getTargetLang());
                hits++;
            }
        }
        if (hits > 0) {
            log.info("Translation cache hits: {}", hits);
        }
        return hits;
    }

    private void updateCache(List<SubtitleLine> batch, TranslationOptions options) {
        for (SubtitleLine line : batch) {
            String sourceText = line.getEffectiveSourceText();
            if (sourceText == null || sourceText.isEmpty()) continue;
            String targetText = line.getEffectiveTargetText();
            if (targetText != null && !targetText.isEmpty()) {
                translationCache.put(hashKey(sourceText, options.getTargetLang(), options.getPrompt()), targetText);
            }
        }
    }

    private String hashKey(String text, String targetLang, String prompt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((text + "::" + (targetLang == null ? "" : targetLang) + "::" + (prompt == null ? "" : prompt)).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return text;
        }
    }

    private boolean isCircuitOpen(String provider) {
        FailureState state = failureStates.get(provider);
        if (state == null || state.openUntilEpochMs <= 0) {
            return false;
        }
        if (Instant.now().toEpochMilli() >= state.openUntilEpochMs) {
            state.openUntilEpochMs = 0;
            state.consecutiveFailures = 0;
            return false;
        }
        return true;
    }

    private void recordFailure(String provider) {
        FailureState state = failureStates.computeIfAbsent(provider, key -> new FailureState());
        state.consecutiveFailures++;
        if (state.consecutiveFailures >= Math.max(1, circuitBreakerThreshold)) {
            state.openUntilEpochMs = Instant.now().toEpochMilli() + (long) circuitBreakerCooldownSeconds * 1000L;
            log.warn("Provider {} circuit opened for {} seconds", provider, circuitBreakerCooldownSeconds);
        }
    }

    private void recordSuccess(String provider) {
        FailureState state = failureStates.computeIfAbsent(provider, key -> new FailureState());
        state.consecutiveFailures = 0;
        state.openUntilEpochMs = 0;
    }

    private void recordUnavailable(String provider) {
        failureStates.computeIfAbsent(provider, key -> new FailureState());
    }

    public Map<String, ProviderHealth> getProviderHealth() {
        return failureStates.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new ProviderHealth(
                                entry.getValue().consecutiveFailures,
                                entry.getValue().openUntilEpochMs,
                                isCircuitOpen(entry.getKey())
                        )
                ));
    }

    private static class FailureState {
        private int consecutiveFailures = 0;
        private long openUntilEpochMs = 0;
    }

    public record ProviderHealth(int consecutiveFailures, long openUntilEpochMs, boolean circuitOpen) {}
}
