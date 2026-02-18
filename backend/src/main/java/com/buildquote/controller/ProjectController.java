package com.buildquote.controller;

import com.buildquote.dto.ProjectDto;
import com.buildquote.entity.User;
import com.buildquote.repository.UserRepository;
import com.buildquote.security.UserPrincipal;
import com.buildquote.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Slf4j
public class ProjectController {

    private final ProjectService projectService;
    private final UserRepository userRepository;

    @PostMapping("/parse-and-save")
    public ResponseEntity<?> parseAndSave(@RequestBody Map<String, String> request,
                                          @AuthenticationPrincipal UserPrincipal principal) {
        User user = getUser(principal);
        String description = request.get("description");
        if (description == null || description.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Description is required"));
        }

        ProjectDto project = projectService.parseAndSave(description, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }

    @PostMapping("/parse-file-and-save")
    public ResponseEntity<?> parseFileAndSave(@RequestParam("file") MultipartFile file,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        User user = getUser(principal);
        try {
            ProjectDto project = projectService.parseFileAndSave(file, user);
            return ResponseEntity.status(HttpStatus.CREATED).body(project);
        } catch (Exception e) {
            log.error("Error parsing file: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to parse file: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<ProjectDto>> listProjects(@AuthenticationPrincipal UserPrincipal principal) {
        User user = getUser(principal);
        return ResponseEntity.ok(projectService.listProjects(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProject(@PathVariable UUID id,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        User user = getUser(principal);
        try {
            return ResponseEntity.ok(projectService.getProject(id, user));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateProject(@PathVariable UUID id,
                                           @RequestBody ProjectDto updateDto,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        User user = getUser(principal);
        try {
            return ResponseEntity.ok(projectService.updateProject(id, user, updateDto));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProject(@PathVariable UUID id,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        User user = getUser(principal);
        try {
            projectService.deleteProject(id, user);
            return ResponseEntity.ok(Map.of("message", "Project deleted"));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private User getUser(UserPrincipal principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
