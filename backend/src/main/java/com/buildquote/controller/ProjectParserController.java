package com.buildquote.controller;

import com.buildquote.dto.ProjectParseRequest;
import com.buildquote.dto.ProjectParseResult;
import com.buildquote.service.ProjectParserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Slf4j
public class ProjectParserController {

    private final ProjectParserService projectParserService;

    /**
     * Parse project from text description
     */
    @PostMapping("/parse")
    public ResponseEntity<ProjectParseResult> parseFromText(
            @Valid @RequestBody ProjectParseRequest request) {
        log.info("Received parse request for description: {}",
                request.getDescription().substring(0, Math.min(50, request.getDescription().length())));

        ProjectParseResult result = projectParserService.parseFromText(request.getDescription());
        return ResponseEntity.ok(result);
    }

    /**
     * Parse project from uploaded file (PDF/DOCX/TXT)
     */
    @PostMapping(value = "/parse-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProjectParseResult> parseFromFile(
            @RequestParam("file") MultipartFile file) {
        log.info("Received parse-file request: filename={}, size={}",
                file.getOriginalFilename(), file.getSize());

        try {
            ProjectParseResult result = projectParserService.parseFromFile(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error parsing file: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Parse project from multiple uploaded files
     */
    @PostMapping(value = "/parse-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProjectParseResult> parseFromFiles(
            @RequestParam("files") List<MultipartFile> files) {
        log.info("Received parse-files request: {} files", files.size());
        files.forEach(f -> log.info("  - {} ({})", f.getOriginalFilename(), f.getSize()));

        try {
            ProjectParseResult result = projectParserService.parseFromFiles(files);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error parsing files: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Enrich parsed stages with market prices and supplier counts.
     * Called after user confirms quantities.
     */
    @PostMapping("/estimate-prices")
    public ResponseEntity<ProjectParseResult> estimatePrices(
            @RequestBody ProjectParseResult parseResult) {
        log.info("Received estimate-prices request for {} stages",
                parseResult.getStages() != null ? parseResult.getStages().size() : 0);

        ProjectParseResult enriched = projectParserService.enrichWithPrices(parseResult);
        return ResponseEntity.ok(enriched);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
