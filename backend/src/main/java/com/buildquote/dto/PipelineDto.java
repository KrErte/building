package com.buildquote.dto;

import com.buildquote.entity.Pipeline;
import com.buildquote.entity.PipelineStep;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineDto {

    private UUID id;
    private UUID projectId;
    private String status;
    private Integer currentStep;
    private Integer totalSteps;
    private Integer progressPercent;
    private String errorMessage;
    private List<StepDto> steps;
    private String createdAt;
    private String startedAt;
    private String completedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepDto {
        private UUID id;
        private Integer stepOrder;
        private String stepType;
        private String stepName;
        private String status;
        private String errorMessage;
        private Integer retryCount;
        private String startedAt;
        private String completedAt;

        public static StepDto fromEntity(PipelineStep step) {
            return StepDto.builder()
                    .id(step.getId())
                    .stepOrder(step.getStepOrder())
                    .stepType(step.getStepType())
                    .stepName(step.getStepName())
                    .status(step.getStatus().name())
                    .errorMessage(step.getErrorMessage())
                    .retryCount(step.getRetryCount())
                    .startedAt(step.getStartedAt() != null ? step.getStartedAt().format(DateTimeFormatter.ISO_DATE_TIME) : null)
                    .completedAt(step.getCompletedAt() != null ? step.getCompletedAt().format(DateTimeFormatter.ISO_DATE_TIME) : null)
                    .build();
        }
    }

    public static PipelineDto fromEntity(Pipeline pipeline) {
        int totalSteps = pipeline.getTotalSteps() != null ? pipeline.getTotalSteps() : 0;
        int completed = pipeline.getSteps() != null
                ? (int) pipeline.getSteps().stream()
                    .filter(s -> s.getStatus() == PipelineStep.StepStatus.COMPLETED
                            || s.getStatus() == PipelineStep.StepStatus.SKIPPED)
                    .count()
                : 0;
        int progress = totalSteps > 0 ? (completed * 100) / totalSteps : 0;

        List<StepDto> stepDtos = pipeline.getSteps() != null
                ? pipeline.getSteps().stream().map(StepDto::fromEntity).collect(Collectors.toList())
                : List.of();

        return PipelineDto.builder()
                .id(pipeline.getId())
                .projectId(pipeline.getProject() != null ? pipeline.getProject().getId() : null)
                .status(pipeline.getStatus().name())
                .currentStep(pipeline.getCurrentStep())
                .totalSteps(totalSteps)
                .progressPercent(progress)
                .errorMessage(pipeline.getErrorMessage())
                .steps(stepDtos)
                .createdAt(pipeline.getCreatedAt() != null ? pipeline.getCreatedAt().format(DateTimeFormatter.ISO_DATE_TIME) : null)
                .startedAt(pipeline.getStartedAt() != null ? pipeline.getStartedAt().format(DateTimeFormatter.ISO_DATE_TIME) : null)
                .completedAt(pipeline.getCompletedAt() != null ? pipeline.getCompletedAt().format(DateTimeFormatter.ISO_DATE_TIME) : null)
                .build();
    }
}
