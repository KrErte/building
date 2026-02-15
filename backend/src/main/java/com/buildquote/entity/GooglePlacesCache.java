package com.buildquote.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "google_places_cache", indexes = {
    @Index(name = "idx_cache_category_location", columnList = "category, location"),
    @Index(name = "idx_cache_searched_at", columnList = "searchedAt")
})
@Data
public class GooglePlacesCache {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String location;

    @Column(columnDefinition = "TEXT")
    private String resultsJson; // JSON array of PlaceResult

    private Integer resultCount;

    @Column(nullable = false)
    private LocalDateTime searchedAt;

    private LocalDateTime lastUsedAt;

    private Integer useCount = 0;
}
