package com.buildquote.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ProjectStageDto {
    private String name;
    private String category;
    private BigDecimal quantity;
    private String unit;
    private String description;
    private List<String> dependencies;

    // Price estimates from market_prices
    private BigDecimal priceEstimateMin;
    private BigDecimal priceEstimateMax;
    private BigDecimal priceEstimateMedian;

    // Supplier count
    private int supplierCount;
}
