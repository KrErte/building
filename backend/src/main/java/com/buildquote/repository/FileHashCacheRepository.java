package com.buildquote.repository;

import com.buildquote.entity.FileHashCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface FileHashCacheRepository extends JpaRepository<FileHashCache, UUID> {

    Optional<FileHashCache> findByCacheKeyAndOperationType(String cacheKey, String operationType);

    @Modifying
    @Query("DELETE FROM FileHashCache f WHERE f.expiresAt < :now")
    int deleteExpired(LocalDateTime now);
}
