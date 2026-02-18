package com.buildquote.pipeline.handlers;

import com.buildquote.entity.Project;
import com.buildquote.entity.ProjectStage;
import com.buildquote.entity.RfqCampaign;
import com.buildquote.pipeline.PipelineContext;
import com.buildquote.pipeline.StepHandler;
import com.buildquote.pipeline.StepResult;
import com.buildquote.repository.ProjectRepository;
import com.buildquote.service.RfqService;
import com.buildquote.dto.RfqRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class SendRfqsStepHandler implements StepHandler {

    private final ProjectRepository projectRepository;
    private final RfqService rfqService;

    @Override
    public String getStepType() {
        return "SEND_RFQS";
    }

    @Override
    public StepResult execute(PipelineContext context) {
        UUID projectId = context.getProjectId();
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        List<String> campaignIds = new ArrayList<>();
        int totalSent = 0;
        int deferred = 0;

        for (ProjectStage stage : project.getStages()) {
            // Phase 4: Horizon filtering - only send RFQs for stages within quoting horizon
            if (shouldDefer(stage, project)) {
                stage.setProcurementStatus("DEFERRED");
                deferred++;
                log.info("Deferred RFQ for stage '{}' - planned start {} is beyond horizon",
                        stage.getName(), stage.getPlannedStartDate());
                continue;
            }

            try {
                RfqRequest request = new RfqRequest();
                request.setTitle(project.getTitle() + " - " + stage.getName());
                request.setCategory(stage.getCategory());
                request.setLocation(project.getLocation());
                request.setQuantity(stage.getQuantity());
                request.setUnit(stage.getUnit());
                request.setSpecifications(stage.getDescription());

                // Pass matched supplier IDs to target specific suppliers
                if (stage.getMatchedSuppliersJson() != null) {
                    // The RfqService will use its own supplier lookup if no IDs specified
                    // but we could parse the matched JSON for supplier IDs here
                }

                var campaign = rfqService.createAndSendCampaign(request);

                // Generate and set reference code: BQ-{seq}-{stageOrder}
                String refCode = String.format("BQ-%04d-%02d",
                        Math.abs(project.getId().hashCode() % 10000),
                        stage.getStageOrder());
                rfqService.setReferenceCode(campaign.getId(), refCode);

                campaignIds.add(campaign.getId());
                totalSent += campaign.getTotalSent();

                stage.setProcurementStatus("ACTIVE");
                log.info("RFQ campaign created for stage '{}': {} (ref: {})",
                        stage.getName(), campaign.getId(), refCode);
            } catch (Exception e) {
                log.warn("Failed to create RFQ for stage '{}': {}", stage.getName(), e.getMessage());
            }
        }

        projectRepository.save(project);

        context.put("campaignIds", campaignIds);
        context.put("totalRfqsSent", totalSent);

        return StepResult.success(Map.of(
                "campaignsCreated", campaignIds.size(),
                "totalSent", totalSent,
                "deferred", deferred
        ));
    }

    private boolean shouldDefer(ProjectStage stage, Project project) {
        if (stage.getPlannedStartDate() == null || project.getQuotingHorizonDays() == null) {
            return false; // No timeline info, proceed normally
        }
        LocalDate horizonEnd = LocalDate.now().plusDays(project.getQuotingHorizonDays());
        return stage.getPlannedStartDate().isAfter(horizonEnd);
    }
}
