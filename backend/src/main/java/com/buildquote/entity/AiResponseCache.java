package com.buildquote.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_response_cache")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiResponseCache {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String cacheKey;

    private String model;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String responseText;

    private Integer inputTokens;
    private Integer outputTokens;
    private BigDecimal costEstimate;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Builder.Default
    private Integer hitCount = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
