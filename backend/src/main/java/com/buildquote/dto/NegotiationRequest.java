package com.buildquote.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NegotiationRequest {
    private BigDecimal targetPrice;
    private String message;
    private String tone; // FRIENDLY, FIRM, AGGRESSIVE
}
