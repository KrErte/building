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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BidRanking {
        private String supplierName;
        private Integer rank;
        private Integer score;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskFlag {
        private String supplierName;
        private String flag;
    }
}
