package com.buildquote.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaterialSummaryDto {
    private String name;
    private String type; // "WORK" or "MATERIAL"
    private BigDecimal quantity;
    private String unit;
    private BigDecimal priceMin;
    private BigDecimal priceMax;
    private String category;
}
