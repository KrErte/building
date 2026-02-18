package com.buildquote.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class PipeSystemDto {
    private String systemCode;    // SK, OK, RV, VV, K, DR, etc.
    private String systemName;    // Full name (e.g., "Sademeveekanalisatsioon")
    private String systemType;    // SEWAGE, RAINWATER, WATER_SUPPLY, VENTILATION, HEATING, DRAIN
    private BigDecimal lengthMeters;  // Total pipe length for this system
    private String pipeSpecs;     // General pipe specifications (e.g., "PP DN110")
    private double confidence;    // 0.0 - 1.0

    private List<PipeComponentDto> components;

    // Pricing totals
    private BigDecimal totalPriceMin;
    private BigDecimal totalPriceMax;
}
