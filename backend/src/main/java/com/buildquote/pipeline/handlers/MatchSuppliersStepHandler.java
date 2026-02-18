package com.buildquote.pipeline.handlers;

import com.buildquote.entity.Project;
import com.buildquote.entity.ProjectStage;
import com.buildquote.pipeline.PipelineContext;
import com.buildquote.pipeline.StepHandler;
import com.buildquote.pipeline.StepResult;
import com.buildquote.repository.ProjectRepository;
import com.buildquote.service.SupplierMatchingService;
import com.buildquote.service.SupplierMatchingService.ScoredSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class MatchSuppliersStepHandler implements StepHandler {

    private final ProjectRepository projectRepository;
    private final SupplierMatchingService supplierMatchingService;

    @Override
    public String getStepType() {
        return "MATCH_SUPPLIERS";
    }

    @Override
    public StepResult execute(PipelineContext context) {
        UUID projectId = context.getProjectId();
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        String location = project.getLocation() != null ? project.getLocation() : "Tallinn";
        Map<String, Integer> supplierCounts = new HashMap<>();
        List<String> allSupplierIds = new ArrayList<>();
        int totalMatched = 0;

        for (ProjectStage stage : project.getStages()) {
            String category = stage.getCategory();
            if (category == null) continue;

            List<ScoredSupplier> scored = supplierMatchingService.findAndScoreSuppliers(stage, location);

            // Store top matches as JSON in the stage
            stage.setMatchedSuppliersJson(supplierMatchingService.toJson(scored));
            stage.setSupplierCount(scored.size());

            supplierCounts.put(category, scored.size());
            totalMatched += scored.size();

            // Collect supplier IDs for downstream steps (SendRfqs)
            List<String> stageSupplierIds = scored.stream()
                    .map(s -> s.getSupplierId().toString())
                    .collect(Collectors.toList());
            allSupplierIds.addAll(stageSupplierIds);
        }

        projectRepository.save(project);

        // Put supplier IDs in context for SendRfqs step
        context.put("supplierCounts", supplierCounts);
        context.put("totalSuppliersMatched", totalMatched);
        context.put("matchedSupplierIds", allSupplierIds);

        log.info("Multi-factor scored {} suppliers across {} categories for project {}",
                totalMatched, supplierCounts.size(), projectId);
        return StepResult.success(Map.of("totalMatched", totalMatched, "categories", supplierCounts));
    }
}
