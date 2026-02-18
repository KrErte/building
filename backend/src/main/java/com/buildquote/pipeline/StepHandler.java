package com.buildquote.pipeline;

public interface StepHandler {

    String getStepType();

    StepResult execute(PipelineContext context);

    default boolean canRetry() {
        return true;
    }

    default int maxRetries() {
        return 3;
    }
}
