package com.buildquote.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "market_prices")
@Data
public class MarketPrice {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String category;

    private String subcategory;

    @Column(nullable = false)
    private String unit;

    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private BigDecimal medianPrice;
    private BigDecimal avgPrice;

    private Integer sampleCount = 0;

    private String region;
    private BigDecimal regionMultiplier = BigDecimal.ONE;

    private String source;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated = LocalDateTime.now();
}
