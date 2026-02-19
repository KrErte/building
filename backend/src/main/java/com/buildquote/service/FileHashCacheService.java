package com.buildquote.service;

import com.buildquote.entity.FileHashCache;
import com.buildquote.repository.FileHashCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileHashCacheService {

    private final FileHashCacheRepository cacheRepository;

    @Transactional(readOnly = true)
    public Optional<String> getCachedResult(String sha256, String operationType) {
        Optional<FileHashCache> cached = cacheRepository.findByCacheKeyAndOperationType(sha256, operationType);
        if (cached.isPresent()) {
            FileHashCache entry = cached.get();
            if (entry.getExpiresAt().isAfter(LocalDateTime.now())) {
                entry.setHitCount(entry.getHitCount() + 1);
                cacheRepository.save(entry);
                log.info("File hash cache HIT for {} (type={}, hits={})", sha256.substring(0, 12), operationType, entry.getHitCount());
                return Optional.of(entry.getResponseJson());
            }
        }
        return Optional.empty();
    }

    @Transactional
    public void cacheResult(String sha256, String operationType, String promptHash, String resultJson, int ttlHours) {
        // Upsert: delete old entry if exists, insert new
        cacheRepository.findByCacheKeyAndOperationType(sha256, operationType)
                .ifPresent(cacheRepository::delete);

        FileHashCache entry = FileHashCache.builder()
                .cacheKey(sha256)
                .operationType(operationType)
                .promptHash(promptHash)
                .responseJson(resultJson)
                .hitCount(0)
                .expiresAt(LocalDateTime.now().plusHours(ttlHours))
                .build();
        cacheRepository.save(entry);
        log.info("Cached file hash result for {} (type={}, ttl={}h)", sha256.substring(0, 12), operationType, ttlHours);
    }

    public String computeSha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public String computePromptHash(String prompt) {
        return computeSha256(prompt.getBytes(StandardCharsets.UTF_8));
    }

    @Scheduled(fixedRate = 21600000) // Every 6 hours
    @Transactional
    public int evictExpired() {
        int deleted = cacheRepository.deleteExpired(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Evicted {} expired file hash cache entries", deleted);
        }
        return deleted;
    }
}
