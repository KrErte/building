package com.buildquote.pipeline;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepResult {

    private Status status;
    private Map<String, Object> outputData;
    private String message;
    private String errorMessage;

    public enum Status {
        SUCCESS, FAILED, AWAITING
    }

    public static StepResult success(Map<String, Object> data) {
        return StepResult.builder().status(Status.SUCCESS).outputData(data).build();
    }

    public static StepResult success(String message) {
        return StepResult.builder().status(Status.SUCCESS).message(message).build();
    }

    public static StepResult failed(String error) {
        return StepResult.builder().status(Status.FAILED).errorMessage(error).build();
    }

    public static StepResult awaiting(String message) {
        return StepResult.builder().status(Status.AWAITING).message(message).build();
    }
}
