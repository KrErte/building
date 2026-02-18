package com.buildquote.dto;

import com.buildquote.entity.Project;
import com.buildquote.entity.ProjectStage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDto {

    private UUID id;
    private String title;
    private String location;
    private BigDecimal budget;
    private String status;
    private String description;
    private String deadline;
    private BigDecimal totalEstimateMin;
    private BigDecimal totalEstimateMax;
    private Integer totalSupplierCount;
    private BigDecimal parseConfidence;
    private String validationStatus;
    private Integer quotingHorizonDays;
    private String constructionStartDate;
    private List<StageDto> stages;
    private String createdAt;
    private String updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageDto {
        private UUID id;
        private Integer stageOrder;
        private String name;
        private String category;
        private BigDecimal quantity;
        private String unit;
        private String description;
        private BigDecimal priceEstimateMin;
        private BigDecimal priceEstimateMax;
        private BigDecimal priceEstimateMedian;
        private String status;
        private Integer supplierCount;
        private String emtakCode;
        private BigDecimal validationConfidence;
        private String validationIssues;
        private String matchedSuppliersJson;
        private String plannedStartDate;
        private Integer plannedDurationDays;
        private String procurementStatus;

        public static StageDto fromEntity(ProjectStage stage) {
            return StageDto.builder()
                    .id(stage.getId())
                    .stageOrder(stage.getStageOrder())
                    .name(stage.getName())
                    .category(stage.getCategory())
                    .quantity(stage.getQuantity())
                    .unit(stage.getUnit())
                    .description(stage.getDescription())
                    .priceEstimateMin(stage.getPriceEstimateMin())
                    .priceEstimateMax(stage.getPriceEstimateMax())
                    .priceEstimateMedian(stage.getPriceEstimateMedian())
                    .status(stage.getStatus().name())
                    .supplierCount(stage.getSupplierCount())
                    .emtakCode(stage.getEmtakCode())
                    .validationConfidence(stage.getValidationConfidence())
                    .validationIssues(stage.getValidationIssues())
                    .matchedSuppliersJson(stage.getMatchedSuppliersJson())
                    .plannedStartDate(stage.getPlannedStartDate() != null ? stage.getPlannedStartDate().toString() : null)
                    .plannedDurationDays(stage.getPlannedDurationDays())
                    .procurementStatus(stage.getProcurementStatus())
                    .build();
        }
    }

    public static ProjectDto fromEntity(Project project) {
        List<StageDto> stageDtos = project.getStages() != null
                ? project.getStages().stream().map(StageDto::fromEntity).collect(Collectors.toList())
                : List.of();

        return ProjectDto.builder()
                .id(project.getId())
                .title(project.getTitle())
                .location(project.getLocation())
                .budget(project.getBudget())
                .status(project.getStatus().name())
                .description(project.getDescription())
                .deadline(project.getDeadline())
                .totalEstimateMin(project.getTotalEstimateMin())
                .totalEstimateMax(project.getTotalEstimateMax())
                .totalSupplierCount(project.getTotalSupplierCount())
                .parseConfidence(project.getParseConfidence())
                .validationStatus(project.getValidationStatus())
                .quotingHorizonDays(project.getQuotingHorizonDays())
                .constructionStartDate(project.getConstructionStartDate() != null ? project.getConstructionStartDate().toString() : null)
                .stages(stageDtos)
                .createdAt(project.getCreatedAt() != null ? project.getCreatedAt().format(DateTimeFormatter.ISO_DATE_TIME) : null)
                .updatedAt(project.getUpdatedAt() != null ? project.getUpdatedAt().format(DateTimeFormatter.ISO_DATE_TIME) : null)
                .build();
    }
}
