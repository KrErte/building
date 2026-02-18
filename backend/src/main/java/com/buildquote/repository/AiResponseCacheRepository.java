package com.buildquote.repository;

import com.buildquote.entity.AiResponseCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiResponseCacheRepository extends JpaRepository<AiResponseCache, UUID> {

    Optional<AiResponseCache> findByCacheKeyAndExpiresAtAfter(String cacheKey, LocalDateTime now);

    @Modifying
    @Query("UPDATE AiResponseCache c SET c.hitCount = c.hitCount + 1 WHERE c.id = :id")
    void incrementHitCount(@Param("id") UUID id);

    @Modifying
    @Query("DELETE FROM AiResponseCache c WHERE c.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
