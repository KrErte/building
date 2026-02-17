package com.buildquote.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BidPageDto {
    private String token;
    private String campaignTitle;
    private String category;
    private String location;
    private BigDecimal quantity;
    private String unit;
    private String specifications;
    private String deadline;
    private BigDecimal maxBudget;
    private String supplierName;
    private String supplierEmail;
    private boolean alreadySubmitted;
    private String existingBidPrice;
    private String existingBidNotes;
}
