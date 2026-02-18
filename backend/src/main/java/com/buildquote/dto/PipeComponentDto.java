package com.buildquote.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PipeComponentDto {
    private String type;          // STRAIGHT, ELBOW_90, ELBOW_45, TEE, COUPLING, REDUCER, VALVE, MANHOLE, DRAIN, CLEANOUT
    private String typeLabel;     // Human-readable label
    private int diameterMm;       // Pipe diameter in mm (e.g., 50, 110, 160)
    private int count;            // Number of this component
    private BigDecimal lengthM;   // Total length in meters (for straight pipes)
    private String material;      // PP, PVC, PE, copper, steel, etc.
    private double confidence;    // 0.0 - 1.0

    // Pricing
    private BigDecimal unitPriceMin;
    private BigDecimal unitPriceMax;
    private BigDecimal totalPriceMin;
    private BigDecimal totalPriceMax;
    private String priceUnit;     // jm (linear meter) or tk (piece)
}
