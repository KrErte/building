package com.buildquote.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComparisonResultDto {

    private UUID campaignId;
    private Integer totalBids;
    private String bestValue;
    private String recommendation;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private BigDecimal medianPrice;
    private List<BidRanking> rankings;
    private List<RiskFlag> riskFlags;
    private List<NegotiationTarget> negotiationTargets;
    private PriceRange priceRange;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BidRanking {
        private String supplierName;
        private Integer rank;
        private Integer score;
        private String reason;
        // New weighted fields
        private BigDecimal weightedScore;
        private BigDecimal completeness;
        private String priceAssessment;
        private List<String> redFlags;
        private List<LineItemAnalysis> lineItemAnalysis;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskFlag {
        private String supplierName;
        private String flag;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NegotiationTarget {
        private String supplierName;
        private UUID bidId;
        private BigDecimal targetPrice;
        private BigDecimal discountPercent;
        private String reasoning;
        private String leverage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItemAnalysis {
        private String item;
        private BigDecimal bidPrice;
        private BigDecimal marketPrice;
        private String assessment;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceRange {
        private BigDecimal min;
        private BigDecimal max;
        private BigDecimal median;
        private String marketAssessment;
    }
}
