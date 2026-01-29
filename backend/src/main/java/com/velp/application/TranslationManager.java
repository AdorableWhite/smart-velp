package com.velp.application;

import com.velp.domain.model.SubtitleLine;
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
    public void translate(List<SubtitleLine> subtitles) {
        translate(subtitles, null);
    }

    @Override
    public void translate(List<SubtitleLine> subtitles, java.util.function.Consumer<Integer> progressCallback) {
        if (subtitles == null || subtitles.isEmpty()) {
            if (progressCallback != null) progressCallback.accept(100);
            return;
        }

        List<SubtitleLine> candidates = subtitles.stream()
                .filter(s -> (s.getCn() == null || s.getCn().isEmpty()) && s.getEn() != null && !s.getEn().isEmpty())
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            if (progressCallback != null) progressCallback.accept(100);
            return;
        }

        int totalToTranslate = candidates.size();
        applyCache(candidates);
        candidates = candidates.stream()
                .filter(s -> (s.getCn() == null || s.getCn().isEmpty()) && s.getEn() != null && !s.getEn().isEmpty())
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

        List<String> providerChain = buildProviderChain();
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
            translateBatchWithFallback(batch, providerChain);
            updateCache(batch);
            translatedCount += batch.size();
            if (progressCallback != null) {
                progressCallback.accept(Math.min(100, translatedCount * 100 / totalToTranslate));
            }
        }
    }

    private List<String> buildProviderChain() {
        List<String> providers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
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

    private void translateBatchWithFallback(List<SubtitleLine> batch, List<String> providerChain) {
        RuntimeException lastError = null;
        for (String provider : providerChain) {
            if (isCircuitOpen(provider)) {
                log.warn("Provider {} is in cooldown, skipping", provider);
                continue;
            }
            TranslationService service = factory.getService(provider);
            if (service == null) {
                log.warn("Provider {} not available in factory, skipping", provider);
                continue;
            }
            for (int attempt = 1; attempt <= Math.max(1, maxAttempts); attempt++) {
                long start = System.nanoTime();
                try {
                    service.translate(batch);
                    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                    log.info("Provider {} translated batch size {} in {} ms (attempt {})", provider, batch.size(), elapsedMs, attempt);
                    recordSuccess(provider);
                    return;
                } catch (IllegalStateException e) {
                    log.warn("Provider {} unavailable: {}", provider, e.getMessage());
                    lastError = new RuntimeException(e);
                    recordSuccess(provider);
                    break;
                } catch (Exception e) {
                    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                    log.warn("Provider {} failed attempt {} ({} ms): {}", provider, attempt, elapsedMs, e.getMessage());
                    lastError = new RuntimeException(e);
                    if (attempt >= Math.max(1, maxAttempts)) {
                        recordFailure(provider);
                    }
                }
            }
        }
        if (lastError != null) {
            throw new RuntimeException("All translation providers failed for current batch", lastError);
        }
        throw new RuntimeException("All translation providers unavailable for current batch");
    }

    private int applyCache(List<SubtitleLine> candidates) {
        int hits = 0;
        for (SubtitleLine line : candidates) {
            if (line.getEn() == null || line.getEn().isEmpty()) {
                continue;
            }
            String key = hashKey(line.getEn());
            String cached = translationCache.get(key);
            if (cached != null && !cached.isEmpty()) {
                line.setCn(cached);
                hits++;
            }
        }
        if (hits > 0) {
            log.info("Translation cache hits: {}", hits);
        }
        return hits;
    }

    private void updateCache(List<SubtitleLine> batch) {
        for (SubtitleLine line : batch) {
            if (line.getEn() == null || line.getEn().isEmpty()) continue;
            String cn = line.getCn();
            if (cn != null && !cn.isEmpty()) {
                translationCache.put(hashKey(line.getEn()), cn);
            }
        }
    }

    private String hashKey(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(text.getBytes(StandardCharsets.UTF_8));
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

    private static class FailureState {
        private int consecutiveFailures = 0;
        private long openUntilEpochMs = 0;
    }
}
