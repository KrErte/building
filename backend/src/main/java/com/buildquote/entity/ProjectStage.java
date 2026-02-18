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
@Table(name = "project_stages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectStage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private Integer stageOrder;

    @Column(nullable = false)
    private String name;

    private String category;

    private BigDecimal quantity;

    private String unit;

    @Column(columnDefinition = "TEXT")
    private String description;

    private BigDecimal priceEstimateMin;
    private BigDecimal priceEstimateMax;
    private BigDecimal priceEstimateMedian;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StageStatus status = StageStatus.PENDING;

    private Integer supplierCount;

    @Column(columnDefinition = "TEXT")
    private String dependenciesJson;

    // Phase 1: Validation fields
    private String emtakCode;

    @Column(precision = 5, scale = 2)
    private BigDecimal validationConfidence;

    @Column(columnDefinition = "TEXT")
    private String validationIssues;

    @Column(columnDefinition = "TEXT")
    private String matchedSuppliersJson;

    // Phase 4: Phased procurement fields
    private java.time.LocalDate plannedStartDate;
    private Integer plannedDurationDays;

    @Builder.Default
    private String procurementStatus = "ACTIVE";

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

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

    public enum StageStatus {
        PENDING, IN_PROGRESS, QUOTING, COMPLETED, SKIPPED
    }
}
