package com.buildquote.service;

import com.buildquote.entity.Pipeline;
import com.buildquote.entity.Project;
import com.buildquote.entity.ProjectStage;
import com.buildquote.entity.User;
import com.buildquote.pipeline.PipelineEngine;
import com.buildquote.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs daily at 8 AM on weekdays. Finds deferred stages that are now within the
 * quoting horizon, promotes them to ACTIVE, and creates mini-pipelines for each wave.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProcurementSchedulerService {

    private final ProjectRepository projectRepository;
    private final PipelineEngine pipelineEngine;

    private static final List<String> WAVE_PIPELINE_STEPS = List.of(
            "MATCH_SUPPLIERS", "ENRICH_COMPANIES", "SEND_RFQS", "AWAIT_BIDS", "COMPARE_BIDS"
    );

    /**
     * Daily check at 8 AM weekdays (Mon-Fri).
     * Cron: second minute hour day-of-month month day-of-week
     */
    @Scheduled(cron = "0 0 8 * * MON-FRI")
    @Transactional
    public void promoteDefferredStages() {
        LocalDate today = LocalDate.now();
        List<Project> allProjects = projectRepository.findAll();

        int promoted = 0;
        int pipelinesCreated = 0;

        for (Project project : allProjects) {
            if (project.getQuotingHorizonDays() == null) continue;
            if (project.getStages() == null || project.getStages().isEmpty()) continue;

            LocalDate horizonEnd = today.plusDays(project.getQuotingHorizonDays());
            List<ProjectStage> toPromote = new ArrayList<>();

            for (ProjectStage stage : project.getStages()) {
                if (!"DEFERRED".equals(stage.getProcurementStatus())) continue;
                if (stage.getPlannedStartDate() == null) continue;

                // Stage is now within horizon
                if (!stage.getPlannedStartDate().isAfter(horizonEnd)) {
                    stage.setProcurementStatus("ACTIVE");
                    toPromote.add(stage);
                    promoted++;
                    log.info("Promoted stage '{}' in project '{}' from DEFERRED to ACTIVE",
                            stage.getName(), project.getTitle());
                }
            }

            // Create mini-pipeline for newly promoted stages
            if (!toPromote.isEmpty()) {
                projectRepository.save(project);
                try {
                    User user = project.getUser();
                    if (user != null) {
                        Pipeline wavePipeline = pipelineEngine.createPipeline(
                                user, project, WAVE_PIPELINE_STEPS);
                        pipelineEngine.startPipeline(wavePipeline.getId());
                        pipelinesCreated++;
                        log.info("Created wave pipeline {} for {} promoted stages in project '{}'",
                                wavePipeline.getId(), toPromote.size(), project.getTitle());
                    }
                } catch (Exception e) {
                    log.error("Failed to create wave pipeline for project {}: {}",
                            project.getId(), e.getMessage());
                }
            }
        }

        if (promoted > 0) {
            log.info("Procurement scheduler: promoted {} stages, created {} pipelines",
                    promoted, pipelinesCreated);
        }
    }

    /**
     * Infer construction sequence from stage dependencies and compute planned start dates.
     * Called during validation step if stages have dependency info and project has a construction start date.
     */
    public void inferTimeline(Project project) {
        if (project.getConstructionStartDate() == null) return;
        if (project.getStages() == null || project.getStages().isEmpty()) return;

        LocalDate startDate = project.getConstructionStartDate();
        int defaultDuration = 30; // default 30 days per stage

        // Simple sequential scheduling based on stage order
        // If dependencies exist, they can refine this later
        LocalDate currentDate = startDate;

        for (ProjectStage stage : project.getStages()) {
            stage.setPlannedStartDate(currentDate);

            int duration = stage.getPlannedDurationDays() != null
                    ? stage.getPlannedDurationDays()
                    : defaultDuration;
            stage.setPlannedDurationDays(duration);

            // Next stage starts after this one
            currentDate = currentDate.plusDays(duration);
        }

        projectRepository.save(project);
        log.info("Inferred timeline for project '{}': {} stages from {} to {}",
                project.getTitle(), project.getStages().size(), startDate, currentDate);
    }
}
