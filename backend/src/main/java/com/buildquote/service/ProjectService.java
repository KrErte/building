package com.buildquote.service;

import com.buildquote.dto.ProjectDto;
import com.buildquote.dto.ProjectParseResult;
import com.buildquote.dto.ProjectStageDto;
import com.buildquote.entity.Project;
import com.buildquote.entity.ProjectStage;
import com.buildquote.entity.User;
import com.buildquote.entity.Pipeline;
import com.buildquote.entity.User;
import com.buildquote.pipeline.PipelineEngine;
import com.buildquote.repository.ProjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectParserService projectParserService;
    private final PipelineEngine pipelineEngine;
    private final ObjectMapper objectMapper;

    private static final List<String> AUTO_PIPELINE_STEPS = List.of(
            "VALIDATE_PARSE", "MATCH_SUPPLIERS", "ENRICH_COMPANIES",
            "SEND_RFQS", "AWAIT_BIDS", "COMPARE_BIDS"
    );

    @Transactional
    public ProjectDto parseAndSave(String description, User user) {
        ProjectParseResult parseResult = projectParserService.parseFromText(description);
        Project project = persistParseResult(parseResult, user);
        return ProjectDto.fromEntity(project);
    }

    @Transactional
    public ProjectDto parseFileAndSave(MultipartFile file, User user) throws IOException {
        ProjectParseResult parseResult = projectParserService.parseFromFile(file);
        Project project = persistParseResult(parseResult, user);
        return ProjectDto.fromEntity(project);
    }

    public List<ProjectDto> listProjects(User user) {
        return projectRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(ProjectDto::fromEntity)
                .collect(Collectors.toList());
    }

    public ProjectDto getProject(UUID projectId, User user) {
        Project project = projectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        return ProjectDto.fromEntity(project);
    }

    @Transactional
    public ProjectDto updateProject(UUID projectId, User user, ProjectDto updateDto) {
        Project project = projectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (updateDto.getTitle() != null) project.setTitle(updateDto.getTitle());
        if (updateDto.getLocation() != null) project.setLocation(updateDto.getLocation());
        if (updateDto.getBudget() != null) project.setBudget(updateDto.getBudget());
        if (updateDto.getDescription() != null) project.setDescription(updateDto.getDescription());
        if (updateDto.getDeadline() != null) project.setDeadline(updateDto.getDeadline());
        if (updateDto.getStatus() != null) {
            project.setStatus(Project.ProjectStatus.valueOf(updateDto.getStatus()));
        }

        project = projectRepository.save(project);
        return ProjectDto.fromEntity(project);
    }

    @Transactional
    public void deleteProject(UUID projectId, User user) {
        Project project = projectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        projectRepository.delete(project);
    }

    private Project persistParseResult(ProjectParseResult parseResult, User user) {
        Project project = Project.builder()
                .user(user)
                .title(parseResult.getProjectTitle())
                .location(parseResult.getLocation())
                .budget(parseResult.getTotalBudget())
                .status(Project.ProjectStatus.PARSED)
                .deadline(parseResult.getDeadline())
                .totalEstimateMin(parseResult.getTotalEstimateMin())
                .totalEstimateMax(parseResult.getTotalEstimateMax())
                .totalSupplierCount(parseResult.getTotalSupplierCount())
                .stages(new ArrayList<>())
                .build();

        project = projectRepository.save(project);

        if (parseResult.getStages() != null) {
            int order = 0;
            for (ProjectStageDto stageDto : parseResult.getStages()) {
                ProjectStage stage = ProjectStage.builder()
                        .project(project)
                        .stageOrder(order++)
                        .name(stageDto.getName())
                        .category(stageDto.getCategory())
                        .quantity(stageDto.getQuantity())
                        .unit(stageDto.getUnit())
                        .description(stageDto.getDescription())
                        .priceEstimateMin(stageDto.getPriceEstimateMin())
                        .priceEstimateMax(stageDto.getPriceEstimateMax())
                        .priceEstimateMedian(stageDto.getPriceEstimateMedian())
                        .supplierCount(stageDto.getSupplierCount())
                        .status(ProjectStage.StageStatus.PENDING)
                        .build();
                project.getStages().add(stage);
            }
            project = projectRepository.save(project);
        }

        log.info("Project saved: {} with {} stages", project.getId(), project.getStages().size());

        // Auto-trigger pipeline after project save
        autoTriggerPipeline(project, user);

        return project;
    }

    private void autoTriggerPipeline(Project project, User user) {
        try {
            Pipeline pipeline = pipelineEngine.createPipeline(user, project, AUTO_PIPELINE_STEPS);
            pipelineEngine.startPipeline(pipeline.getId());
            log.info("Auto-triggered pipeline {} for project {}", pipeline.getId(), project.getId());
        } catch (Exception e) {
            log.error("Failed to auto-trigger pipeline for project {}: {}",
                    project.getId(), e.getMessage());
        }
    }
}
