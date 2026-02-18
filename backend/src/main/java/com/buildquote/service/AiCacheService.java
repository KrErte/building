package com.buildquote.service;

import com.buildquote.entity.AiResponseCache;
import com.buildquote.repository.AiResponseCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiCacheService {

    private final AiResponseCacheRepository cacheRepository;

    private static final int DEFAULT_TTL_HOURS = 24;

    public Optional<String> getCached(String prompt, String model) {
        String key = generateCacheKey(prompt, model);
        Optional<AiResponseCache> cached = cacheRepository.findByCacheKeyAndExpiresAtAfter(key, LocalDateTime.now());

        if (cached.isPresent()) {
            cacheRepository.incrementHitCount(cached.get().getId());
            log.debug("AI cache hit for key: {}", key.substring(0, 16));
            return Optional.of(cached.get().getResponseText());
        }

        return Optional.empty();
    }

    @Transactional
    public void cache(String prompt, String model, String response) {
        cache(prompt, model, response, DEFAULT_TTL_HOURS);
    }

    @Transactional
    public void cache(String prompt, String model, String response, int ttlHours) {
        String key = generateCacheKey(prompt, model);

        AiResponseCache entry = AiResponseCache.builder()
                .cacheKey(key)
                .model(model)
                .responseText(response)
                .expiresAt(LocalDateTime.now().plusHours(ttlHours))
                .hitCount(0)
                .build();

        try {
            cacheRepository.save(entry);
            log.debug("AI response cached with key: {}", key.substring(0, 16));
        } catch (Exception e) {
            // Duplicate key - update existing
            log.debug("Cache entry already exists for key: {}", key.substring(0, 16));
        }
    }

    @Scheduled(fixedRate = 3600000) // Every hour
    @Transactional
    public void cleanupExpired() {
        int deleted = cacheRepository.deleteExpired(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired AI cache entries", deleted);
        }
    }

    private String generateCacheKey(String prompt, String model) {
        try {
            String input = model + ":" + prompt;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
