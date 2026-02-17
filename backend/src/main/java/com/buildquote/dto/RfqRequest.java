package com.buildquote.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class RfqRequest {
    private String title;
    private String category;
    private String location;
    private BigDecimal quantity;
    private String unit;
    private String specifications;
    private BigDecimal maxBudget;
    private String deadline;
    private List<String> supplierIds;
}
