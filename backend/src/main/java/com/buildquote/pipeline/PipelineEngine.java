package com.buildquote.pipeline;

import com.buildquote.entity.Pipeline;
import com.buildquote.entity.PipelineStep;
import com.buildquote.entity.Project;
import com.buildquote.entity.User;
import com.buildquote.repository.PipelineRepository;
import com.buildquote.repository.PipelineStepRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class PipelineEngine {

    private final PipelineRepository pipelineRepository;
    private final PipelineStepRepository stepRepository;
    private final List<StepHandler> stepHandlers;
    private final ObjectMapper objectMapper;

    private Map<String, StepHandler> handlerMap;

    private Map<String, StepHandler> getHandlerMap() {
        if (handlerMap == null) {
            handlerMap = new HashMap<>();
            for (StepHandler handler : stepHandlers) {
                handlerMap.put(handler.getStepType(), handler);
            }
        }
        return handlerMap;
    }

    @Transactional
    public Pipeline createPipeline(User user, Project project, List<String> stepTypes) {
        Pipeline pipeline = Pipeline.builder()
                .user(user)
                .project(project)
                .status(Pipeline.PipelineStatus.CREATED)
                .currentStep(0)
                .totalSteps(stepTypes.size())
                .steps(new ArrayList<>())
                .build();

        pipeline = pipelineRepository.save(pipeline);

        for (int i = 0; i < stepTypes.size(); i++) {
            String stepType = stepTypes.get(i);
            StepHandler handler = getHandlerMap().get(stepType);
            String stepName = handler != null ? stepType : stepType;

            PipelineStep step = PipelineStep.builder()
                    .pipeline(pipeline)
                    .stepOrder(i)
                    .stepType(stepType)
                    .stepName(stepName)
                    .status(PipelineStep.StepStatus.PENDING)
                    .retryCount(0)
                    .build();

            pipeline.getSteps().add(step);
        }

        pipeline = pipelineRepository.save(pipeline);
        log.info("Pipeline {} created with {} steps for project {}",
                pipeline.getId(), stepTypes.size(), project != null ? project.getId() : "none");
        return pipeline;
    }

    @Async
    public void startPipeline(UUID pipelineId) {
        executePipeline(pipelineId);
    }

    @Transactional
    public void resumePipeline(UUID pipelineId) {
        Pipeline pipeline = pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new RuntimeException("Pipeline not found"));

        if (pipeline.getStatus() != Pipeline.PipelineStatus.PAUSED
                && pipeline.getStatus() != Pipeline.PipelineStatus.FAILED) {
            throw new RuntimeException("Pipeline cannot be resumed from status: " + pipeline.getStatus());
        }

        // Validate that all previous steps are COMPLETED before resuming
        List<PipelineStep> steps = stepRepository.findByPipelineOrderByStepOrderAsc(pipeline);
        for (int i = 0; i < pipeline.getCurrentStep(); i++) {
            PipelineStep step = steps.get(i);
            if (step.getStatus() != PipelineStep.StepStatus.COMPLETED
                    && step.getStatus() != PipelineStep.StepStatus.SKIPPED) {
                throw new RuntimeException("Cannot resume: step " + i + " (" + step.getStepType() + ") is not completed");
            }
        }

        // Reset failed step if resuming from FAILED
        if (pipeline.getStatus() == Pipeline.PipelineStatus.FAILED) {
            if (pipeline.getCurrentStep() < steps.size()) {
                PipelineStep failedStep = steps.get(pipeline.getCurrentStep());
                if (failedStep.getStatus() == PipelineStep.StepStatus.FAILED) {
                    failedStep.setStatus(PipelineStep.StepStatus.PENDING);
                    failedStep.setRetryCount(0);
                    failedStep.setErrorMessage(null);
                    failedStep.setNextRetryAt(null);
                    stepRepository.save(failedStep);
                    log.info("Reset failed step {} ({}) for pipeline resume",
                            failedStep.getStepOrder(), failedStep.getStepType());
                }
            }
            pipeline.setErrorMessage(null);
        }

        pipeline.setStatus(Pipeline.PipelineStatus.RUNNING);
        pipelineRepository.save(pipeline);
        executePipeline(pipelineId);
    }

    @Transactional
    public void cancelPipeline(UUID pipelineId) {
        Pipeline pipeline = pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new RuntimeException("Pipeline not found"));

        pipeline.setStatus(Pipeline.PipelineStatus.CANCELLED);
        pipeline.setCompletedAt(LocalDateTime.now());
        pipelineRepository.save(pipeline);
        log.info("Pipeline {} cancelled", pipelineId);
    }

    public Pipeline getPipelineStatus(UUID pipelineId) {
        return pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new RuntimeException("Pipeline not found"));
    }

    /**
     * Auto-resume pipelines paused at AWAIT_BIDS every 5 minutes.
     * Pipelines paused at VALIDATE_PARSE require human review and are NOT auto-resumed.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    @Transactional
    public void autoResumePausedPipelines() {
        List<Pipeline> paused = pipelineRepository.findByStatus(Pipeline.PipelineStatus.PAUSED);
        for (Pipeline pipeline : paused) {
            // Only auto-resume if paused at AWAIT_BIDS step
            List<PipelineStep> steps = stepRepository.findByPipelineOrderByStepOrderAsc(pipeline);
            if (pipeline.getCurrentStep() < steps.size()) {
                PipelineStep currentStep = steps.get(pipeline.getCurrentStep());
                if ("AWAIT_BIDS".equals(currentStep.getStepType())) {
                    log.info("Auto-resuming pipeline {} paused at AWAIT_BIDS", pipeline.getId());
                    try {
                        pipeline.setStatus(Pipeline.PipelineStatus.RUNNING);
                        pipelineRepository.save(pipeline);
                        executePipeline(pipeline.getId());
                    } catch (Exception e) {
                        log.error("Failed to auto-resume pipeline {}: {}", pipeline.getId(), e.getMessage());
                    }
                }
            }
        }
    }

    private void executePipeline(UUID pipelineId) {
        Pipeline pipeline = pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new RuntimeException("Pipeline not found"));

        pipeline.setStatus(Pipeline.PipelineStatus.RUNNING);
        pipeline.setStartedAt(LocalDateTime.now());
        pipelineRepository.save(pipeline);

        PipelineContext context = PipelineContext.fromJson(pipeline.getContextJson());
        context.setPipelineId(pipelineId);
        if (pipeline.getProject() != null) {
            context.setProjectId(pipeline.getProject().getId());
        }
        context.setUserId(pipeline.getUser().getId());

        List<PipelineStep> steps = stepRepository.findByPipelineOrderByStepOrderAsc(pipeline);

        for (int i = pipeline.getCurrentStep(); i < steps.size(); i++) {
            PipelineStep step = steps.get(i);

            // Skip already completed steps
            if (step.getStatus() == PipelineStep.StepStatus.COMPLETED
                    || step.getStatus() == PipelineStep.StepStatus.SKIPPED) {
                continue;
            }

            // Check if pipeline was cancelled
            Pipeline current = pipelineRepository.findById(pipelineId).orElse(null);
            if (current == null || current.getStatus() == Pipeline.PipelineStatus.CANCELLED) {
                log.info("Pipeline {} was cancelled, stopping execution", pipelineId);
                return;
            }

            StepHandler handler = getHandlerMap().get(step.getStepType());
            if (handler == null) {
                log.warn("No handler for step type: {}, skipping", step.getStepType());
                step.setStatus(PipelineStep.StepStatus.SKIPPED);
                stepRepository.save(step);
                continue;
            }

            // Check nextRetryAt before executing (respect backoff delay)
            if (step.getNextRetryAt() != null && step.getNextRetryAt().isAfter(LocalDateTime.now())) {
                log.info("Pipeline {} step {} has nextRetryAt={}, waiting...",
                        pipelineId, i, step.getNextRetryAt());
                try {
                    long waitMs = java.time.Duration.between(LocalDateTime.now(), step.getNextRetryAt()).toMillis();
                    if (waitMs > 0 && waitMs < 300000) { // Max 5 min wait
                        Thread.sleep(waitMs);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            // Execute step
            step.setStatus(PipelineStep.StepStatus.RUNNING);
            step.setStartedAt(LocalDateTime.now());
            step.setNextRetryAt(null);
            stepRepository.save(step);

            pipeline.setCurrentStep(i);
            pipeline.setContextJson(context.toJson());
            pipelineRepository.save(pipeline);

            try {
                StepResult result = handler.execute(context);

                if (result.getStatus() == StepResult.Status.SUCCESS) {
                    step.setStatus(PipelineStep.StepStatus.COMPLETED);
                    step.setCompletedAt(LocalDateTime.now());
                    if (result.getOutputData() != null) {
                        step.setOutputJson(objectMapper.writeValueAsString(result.getOutputData()));
                    }
                    stepRepository.save(step);
                    log.info("Pipeline {} step {} ({}) completed", pipelineId, i, step.getStepType());

                } else if (result.getStatus() == StepResult.Status.AWAITING) {
                    step.setStatus(PipelineStep.StepStatus.PENDING);
                    stepRepository.save(step);
                    pipeline.setStatus(Pipeline.PipelineStatus.PAUSED);
                    pipeline.setContextJson(context.toJson());
                    pipelineRepository.save(pipeline);
                    log.info("Pipeline {} paused at step {} ({}): awaiting",
                            pipelineId, i, step.getStepType());
                    return;

                } else {
                    handleStepFailure(pipeline, step, handler, context, result.getErrorMessage());
                    if (step.getStatus() == PipelineStep.StepStatus.FAILED) {
                        return; // Pipeline failed
                    }
                }

            } catch (Exception e) {
                log.error("Pipeline {} step {} ({}) threw exception: {}",
                        pipelineId, i, step.getStepType(), e.getMessage(), e);
                handleStepFailure(pipeline, step, handler, context, e.getMessage());
                if (step.getStatus() == PipelineStep.StepStatus.FAILED) {
                    return;
                }
            }
        }

        // All steps completed
        pipeline.setStatus(Pipeline.PipelineStatus.COMPLETED);
        pipeline.setCompletedAt(LocalDateTime.now());
        pipeline.setContextJson(context.toJson());
        pipelineRepository.save(pipeline);
        log.info("Pipeline {} completed successfully", pipelineId);
    }

    private void handleStepFailure(Pipeline pipeline, PipelineStep step,
                                    StepHandler handler, PipelineContext context, String error) {
        step.setRetryCount(step.getRetryCount() + 1);
        step.setErrorMessage(error);

        if (handler.canRetry() && step.getRetryCount() < handler.maxRetries()) {
            // Calculate next retry time with backoff: retryCount * 30 seconds
            LocalDateTime nextRetry = LocalDateTime.now().plusSeconds((long) step.getRetryCount() * 30);
            step.setNextRetryAt(nextRetry);
            step.setStatus(PipelineStep.StepStatus.PENDING);
            stepRepository.save(step);
            log.warn("Pipeline {} step {} failed (attempt {}/{}), next retry at {}: {}",
                    pipeline.getId(), step.getStepOrder(), step.getRetryCount(), handler.maxRetries(),
                    nextRetry, error);
        } else {
            step.setStatus(PipelineStep.StepStatus.FAILED);
            step.setCompletedAt(LocalDateTime.now());
            step.setNextRetryAt(null);
            stepRepository.save(step);

            pipeline.setStatus(Pipeline.PipelineStatus.FAILED);
            pipeline.setErrorMessage("Step '" + step.getStepName() + "' failed: " + error);
            pipeline.setContextJson(context.toJson());
            pipelineRepository.save(pipeline);
            log.error("Pipeline {} failed at step {}: {}", pipeline.getId(), step.getStepOrder(), error);
        }
    }
}
