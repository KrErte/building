package com.buildquote.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProjectParseRequest {
    @NotBlank(message = "Description is required")
    private String description;
}
