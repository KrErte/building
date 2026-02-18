package com.buildquote.dto;

import com.buildquote.entity.CompanyEnrichment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyEnrichmentDto {

    private UUID supplierId;
    private String companyName;
    private String llmSummary;
    private String llmSpecialties;
    private Integer riskScore;
    private Integer reliabilityScore;
    private String priceCompetitiveness;
    private String recommendedFor;
    private boolean tier1Complete;
    private boolean tier2Complete;
    private boolean tier3Complete;

    public static CompanyEnrichmentDto fromEntity(CompanyEnrichment enrichment) {
        return CompanyEnrichmentDto.builder()
                .supplierId(enrichment.getSupplier().getId())
                .companyName(enrichment.getSupplier().getCompanyName())
                .llmSummary(enrichment.getLlmSummary())
                .llmSpecialties(enrichment.getLlmSpecialties())
                .riskScore(enrichment.getRiskScore())
                .reliabilityScore(enrichment.getReliabilityScore())
                .priceCompetitiveness(enrichment.getPriceCompetitiveness())
                .recommendedFor(enrichment.getRecommendedFor())
                .tier1Complete(enrichment.getTier1CompletedAt() != null)
                .tier2Complete(enrichment.getTier2CompletedAt() != null)
                .tier3Complete(enrichment.getTier3CompletedAt() != null)
                .build();
    }
}
