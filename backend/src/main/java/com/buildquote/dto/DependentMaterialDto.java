package com.buildquote.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class DependentMaterialDto {
    private String materialName;
    private BigDecimal totalQuantity;
    private String unit;
    private BigDecimal unitPriceMin;
    private BigDecimal unitPriceMax;
    private BigDecimal totalPriceMin;
    private BigDecimal totalPriceMax;
    private List<String> sourceStages = new ArrayList<>();
}
