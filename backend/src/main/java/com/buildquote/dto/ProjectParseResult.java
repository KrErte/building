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
    private List<PipeSystemDto> pipeSystems;

    // Summary
    private BigDecimal totalEstimateMin;
    private BigDecimal totalEstimateMax;
    private int totalSupplierCount;

    // Dependent materials (component recipe system)
    private List<DependentMaterialDto> dependentMaterials;
    private List<PipeLengthSummaryDto> pipeLengthSummaries;
    private List<MaterialSummaryDto> summary;
    private BigDecimal materialsTotalMin;
    private BigDecimal materialsTotalMax;
    private BigDecimal grandTotalMin;   // stages + materials
    private BigDecimal grandTotalMax;
}
