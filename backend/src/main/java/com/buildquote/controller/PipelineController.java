package com.buildquote.controller;

import com.buildquote.dto.PipelineDto;
import com.buildquote.entity.Pipeline;
import com.buildquote.entity.Project;
import com.buildquote.entity.User;
import com.buildquote.pipeline.PipelineEngine;
import com.buildquote.repository.ProjectRepository;
import com.buildquote.repository.PipelineRepository;
import com.buildquote.repository.UserRepository;
import com.buildquote.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pipelines")
@RequiredArgsConstructor
@Slf4j
public class PipelineController {

    private final PipelineEngine pipelineEngine;
    private final PipelineRepository pipelineRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    private static final List<String> DEFAULT_STEPS = List.of(
            "PARSE_FILES", "VALIDATE_PARSE", "MATCH_SUPPLIERS", "ENRICH_COMPANIES",
            "SEND_RFQS", "AWAIT_BIDS", "COMPARE_BIDS"
    );

    @PostMapping
    public ResponseEntity<?> createAndStart(@RequestBody Map<String, String> request,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        User user = getUser(principal);
        String projectIdStr = request.get("projectId");

        Project project = null;
        if (projectIdStr != null && !projectIdStr.isBlank()) {
            project = projectRepository.findByIdAndUser(UUID.fromString(projectIdStr), user)
                    .orElseThrow(() -> new RuntimeException("Project not found"));
        }

        Pipeline pipeline = pipelineEngine.createPipeline(user, project, DEFAULT_STEPS);
        pipelineEngine.startPipeline(pipeline.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(PipelineDto.fromEntity(pipeline));
    }

    @GetMapping
    public ResponseEntity<List<PipelineDto>> listPipelines(@AuthenticationPrincipal UserPrincipal principal) {
        User user = getUser(principal);
        List<PipelineDto> pipelines = pipelineRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(PipelineDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(pipelines);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getPipeline(@PathVariable UUID id) {
        Pipeline pipeline = pipelineEngine.getPipelineStatus(id);
        return ResponseEntity.ok(PipelineDto.fromEntity(pipeline));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<?> resumePipeline(@PathVariable UUID id) {
        try {
            pipelineEngine.resumePipeline(id);
            return ResponseEntity.ok(Map.of("message", "Pipeline resumed"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<PipelineDto>> findByProject(@PathVariable UUID projectId,
                                                            @AuthenticationPrincipal UserPrincipal principal) {
        User user = getUser(principal);
        // Verify project belongs to user
        projectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        List<PipelineDto> pipelines = pipelineRepository.findByProjectId(projectId).stream()
                .map(PipelineDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(pipelines);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelPipeline(@PathVariable UUID id) {
        pipelineEngine.cancelPipeline(id);
        return ResponseEntity.ok(Map.of("message", "Pipeline cancelled"));
    }

    private User getUser(UserPrincipal principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
