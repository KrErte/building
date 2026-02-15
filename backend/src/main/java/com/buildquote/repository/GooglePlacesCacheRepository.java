package com.buildquote.repository;

import com.buildquote.entity.GooglePlacesCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface GooglePlacesCacheRepository extends JpaRepository<GooglePlacesCache, Long> {

    @Query("SELECT c FROM GooglePlacesCache c WHERE c.category = :category AND c.location = :location AND c.searchedAt > :minDate ORDER BY c.searchedAt DESC")
    Optional<GooglePlacesCache> findValidCache(
        @Param("category") String category,
        @Param("location") String location,
        @Param("minDate") LocalDateTime minDate
    );

    @Modifying
    @Query("UPDATE GooglePlacesCache c SET c.lastUsedAt = :now, c.useCount = c.useCount + 1 WHERE c.id = :id")
    void incrementUseCount(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM GooglePlacesCache c WHERE c.searchedAt < :cutoffDate")
    int deleteOldEntries(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Query("SELECT COUNT(c) FROM GooglePlacesCache c WHERE c.searchedAt > :minDate")
    long countValidEntries(@Param("minDate") LocalDateTime minDate);
}
