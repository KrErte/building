package com.buildquote.pipeline.handlers;

import com.buildquote.entity.Project;
import com.buildquote.pipeline.PipelineContext;
import com.buildquote.pipeline.StepHandler;
import com.buildquote.pipeline.StepResult;
import com.buildquote.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class ParseFilesStepHandler implements StepHandler {

    private final ProjectRepository projectRepository;

    @Override
    public String getStepType() {
        return "PARSE_FILES";
    }

    @Override
    public StepResult execute(PipelineContext context) {
        UUID projectId = context.getProjectId();
        if (projectId == null) {
            return StepResult.failed("No project ID in context");
        }

        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return StepResult.failed("Project not found: " + projectId);
        }

        // Project was already parsed during creation - verify stages exist
        if (project.getStages() == null || project.getStages().isEmpty()) {
            return StepResult.failed("Project has no parsed stages");
        }

        int stageCount = project.getStages().size();
        context.put("stageCount", stageCount);
        context.put("projectTitle", project.getTitle());
        context.put("location", project.getLocation());

        log.info("Parse step complete: project {} has {} stages", projectId, stageCount);
        return StepResult.success(Map.of("stageCount", stageCount, "title", project.getTitle()));
    }
}
