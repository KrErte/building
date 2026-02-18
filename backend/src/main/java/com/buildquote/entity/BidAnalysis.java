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
@Table(name = "bid_analyses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BidAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bid_id")
    private Bid bid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private RfqCampaign campaign;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisType analysisType;

    @Column(columnDefinition = "TEXT")
    private String analysisJson;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String recommendation;

    private BigDecimal confidenceScore;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum AnalysisType {
        PRICE_CHECK, COMPARISON, NEGOTIATION
    }
}
