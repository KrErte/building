package com.buildquote.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ProjectParseResult {
    private String projectTitle;
    private String location;
    private BigDecimal totalBudget;
    private String deadline;
    private List<ProjectStageDto> stages;

    // Summary
    private BigDecimal totalEstimateMin;
    private BigDecimal totalEstimateMax;
    private int totalSupplierCount;
}
