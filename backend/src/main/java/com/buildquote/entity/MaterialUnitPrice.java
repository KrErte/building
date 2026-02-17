package com.buildquote.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "material_unit_prices")
@Data
public class MaterialUnitPrice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String category;

    @Column(name = "material_name", nullable = false)
    private String materialName;

    @Column(nullable = false)
    private String unit;

    @Column(name = "min_price_eur", precision = 10, scale = 2)
    private BigDecimal minPriceEur;

    @Column(name = "max_price_eur", precision = 10, scale = 2)
    private BigDecimal maxPriceEur;

    @Column(name = "avg_price_eur", precision = 10, scale = 2)
    private BigDecimal avgPriceEur;

    private String source;

    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
