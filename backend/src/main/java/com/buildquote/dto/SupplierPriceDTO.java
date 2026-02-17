package com.buildquote.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SupplierPriceDTO(
    String supplierName,
    BigDecimal price,
    String url,
    LocalDate lastUpdated
) {}
