package com.buildquote.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PriceBreakdownDTO(
    List<MaterialLineDTO> materials,
    LaborCostDTO labor,
    OtherCostsDTO otherCosts,
    int confidencePercent,
    String confidenceLabel,
    BigDecimal totalMin,
    BigDecimal totalMax
) {
    public record MaterialLineDTO(
        String name,
        BigDecimal quantity,
        String unit,
        BigDecimal unitPriceMin,
        BigDecimal unitPriceMax,
        String supplierName,
        String supplierUrl,
        String priceSource,
        LocalDate lastUpdated
    ) {}

    public record LaborCostDTO(
        BigDecimal hoursEstimate,
        BigDecimal hourlyRateMin,
        BigDecimal hourlyRateMax,
        BigDecimal totalMin,
        BigDecimal totalMax,
        String source
    ) {}

    public record OtherCostsDTO(
        BigDecimal transportMin,
        BigDecimal transportMax,
        BigDecimal wasteDisposalMin,
        BigDecimal wasteDisposalMax,
        BigDecimal totalMin,
        BigDecimal totalMax
    ) {}
}
