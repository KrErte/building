package com.buildquote.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class BidSubmissionRequest {
    private BigDecimal price;
    private Integer timelineDays;
    private String deliveryDate;
    private String notes;
    private String lineItems;
}
