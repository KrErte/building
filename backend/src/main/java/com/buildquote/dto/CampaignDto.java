package com.buildquote.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CampaignDto {
    private String id;
    private String title;
    private String category;
    private String location;
    private BigDecimal quantity;
    private String unit;
    private String specifications;
    private String deadline;
    private BigDecimal maxBudget;
    private String status;
    private int totalSent;
    private int totalResponded;
    private String createdAt;
    private List<BidDto> bids;
}
