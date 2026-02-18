package com.buildquote.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "company_enrichments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyEnrichment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false, unique = true)
    private Supplier supplier;

    // Tier 1: Crawler facts
    @Column(columnDefinition = "TEXT")
    private String crawlerFactsJson;

    // Tier 2: LLM-generated summary
    @Column(columnDefinition = "TEXT")
    private String llmSummary;

    @Column(columnDefinition = "TEXT")
    private String llmSpecialties;

    // Tier 3: Deep analysis scores
    private Integer riskScore;
    private Integer reliabilityScore;
    private String priceCompetitiveness;

    @Column(columnDefinition = "TEXT")
    private String recommendedFor;

    @Column(columnDefinition = "TEXT")
    private String deepAnalysisJson;

    // Phase 3: Estonian registry fields
    private Boolean taxDebt;

    @Column(precision = 12, scale = 2)
    private java.math.BigDecimal taxDebtAmount;

    private String financialTrend;

    @Column(precision = 14, scale = 2)
    private java.math.BigDecimal annualRevenue;

    private Integer employeeCount;
    private Integer yearsInBusiness;
    private Integer publicProcurementCount;

    @Column(columnDefinition = "TEXT")
    private String registryDataJson;

    private LocalDateTime registryCheckedAt;

    // Tier completion tracking
    private LocalDateTime tier1CompletedAt;
    private LocalDateTime tier2CompletedAt;
    private LocalDateTime tier3CompletedAt;

    // Cache management
    private LocalDateTime cacheExpiresAt;

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
}
