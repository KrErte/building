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
public class BidDto {
    private String id;
    private String supplierName;
    private String supplierEmail;
    private BigDecimal price;
    private String currency;
    private Integer timelineDays;
    private String deliveryDate;
    private String notes;
    private String status;
    private String submittedAt;

    // AI Analysis fields
    private BigDecimal percentFromMedian;  // e.g., -15.5 or +32.0
    private String verdict;  // GREAT_DEAL, FAIR, OVERPRICED, RED_FLAG
    private BigDecimal marketPricePerUnit;
}
