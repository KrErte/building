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
public class NegotiationDto {

    private UUID bidId;
    private String supplierName;
    private BigDecimal currentPrice;
    private BigDecimal targetPrice;
    private BigDecimal discountPercent;
    private List<String> leveragePoints;
    private String counterOfferReasoning;
    private String negotiationTone;
    private String suggestedMessage;
}
