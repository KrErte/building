package com.buildquote.controller;

import com.buildquote.dto.ifc.*;
import com.buildquote.service.IfcProcessingService;
import com.buildquote.service.IfcProcessingService.IfcProcessingJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for IFC file processing endpoints.
 */
@RestController
@RequestMapping("/api/ifc")
@RequiredArgsConstructor
@Slf4j
public class IfcController {

    private final IfcProcessingService ifcProcessingService;

    /**
     * Upload and parse IFC or ZIP file synchronously.
     * Suitable for smaller files (< 10MB).
     * Supports both .ifc files and .zip archives containing IFC files.
     *
     * POST /api/ifc/upload/sync
     * Content-Type: multipart/form-data
     * Body: file (IFC or ZIP file)
     *
     * @param file The IFC or ZIP file
     * @return Parsed building data
     */
    @PostMapping(value = "/upload/sync", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IfcBuildingData> uploadSync(@RequestParam("file") MultipartFile file) {
        log.info("Received sync upload: {} ({}KB)",
            file.getOriginalFilename(), file.getSize() / 1024);

        IfcBuildingData result = ifcProcessingService.processIfcFileSync(file);
        return ResponseEntity.ok(result);
    }

    /**
     * Upload IFC or ZIP file for async processing.
     * Returns job ID for status polling.
     * Supports both .ifc files and .zip archives containing IFC files.
     *
     * POST /api/ifc/upload
     * Content-Type: multipart/form-data
     * Body: file (IFC or ZIP file)
     *
     * @param file The IFC or ZIP file
     * @return 202 Accepted with job ID
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        log.info("Received async upload: {} ({}KB)",
            file.getOriginalFilename(), file.getSize() / 1024);

        String jobId = ifcProcessingService.startProcessingJob(file);

        Map<String, String> response = new HashMap<>();
        response.put("jobId", jobId);
        response.put("status", "PENDING");
        response.put("message", "IFC faili töötlemine alustatud");
        response.put("statusUrl", "/api/ifc/" + jobId + "/status");

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Get the status of an IFC processing job.
     *
     * GET /api/ifc/{jobId}/status
     *
     * @param jobId Job ID
     * @return Job status with result or error
     */
    @GetMapping("/{jobId}/status")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable String jobId) {
        IfcProcessingJob job = ifcProcessingService.getJob(jobId);

        if (job == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Tööd ei leitud");
            response.put("jobId", jobId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", job.getJobId());
        response.put("fileName", job.getFileName());
        response.put("status", job.getStatus().name());
        response.put("elapsedMs", job.getElapsedMs());

        switch (job.getStatus()) {
            case COMPLETED -> {
                response.put("result", job.getResult());
                // Auto-cleanup completed job after retrieval
                ifcProcessingService.removeJob(jobId);
            }
            case FAILED -> {
                response.put("error", job.getError());
                ifcProcessingService.removeJob(jobId);
            }
            case PROCESSING -> {
                response.put("message", "Töötlemine käib...");
            }
            case PENDING -> {
                response.put("message", "Ootel...");
            }
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get summary only from a completed job.
     *
     * GET /api/ifc/{jobId}/summary
     *
     * @param jobId Job ID
     * @return Quantity summary
     */
    @GetMapping("/{jobId}/summary")
    public ResponseEntity<?> getJobSummary(@PathVariable String jobId) {
        IfcProcessingJob job = ifcProcessingService.getJob(jobId);

        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        if (job.getStatus() != IfcProcessingService.IfcProcessingStatus.COMPLETED) {
            Map<String, String> response = new HashMap<>();
            response.put("status", job.getStatus().name());
            response.put("message", "Töö pole veel lõpetatud");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }

        return ResponseEntity.ok(job.getResult().quantitySummary());
    }

    /**
     * Get filtered MEP elements from a completed job.
     *
     * GET /api/ifc/{jobId}/elements
     * Query params: system, storey, type, page, size
     *
     * @param jobId Job ID
     * @param system Filter by system type (heating, water_supply, sewage, ventilation, etc.)
     * @param storey Filter by storey name
     * @param type Filter by IFC type (IfcPipeSegment, IfcDuctSegment, etc.)
     * @param page Page number (0-indexed)
     * @param size Page size (default 50)
     * @return Filtered and paginated MEP elements
     */
    @GetMapping("/{jobId}/elements")
    public ResponseEntity<?> getJobElements(
            @PathVariable String jobId,
            @RequestParam(required = false) String system,
            @RequestParam(required = false) String storey,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        IfcProcessingJob job = ifcProcessingService.getJob(jobId);

        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        if (job.getStatus() != IfcProcessingService.IfcProcessingStatus.COMPLETED) {
            Map<String, String> response = new HashMap<>();
            response.put("status", job.getStatus().name());
            response.put("message", "Töö pole veel lõpetatud");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }

        List<IfcMepElementInfo> elements = job.getResult().mepElements();
        if (elements == null) {
            elements = List.of();
        }

        // Apply filters
        List<IfcMepElementInfo> filtered = elements.stream()
            .filter(e -> system == null || system.equalsIgnoreCase(e.systemType()))
            .filter(e -> storey == null || storey.equals(e.storeyName()))
            .filter(e -> type == null || type.equals(e.ifcType()))
            .collect(Collectors.toList());

        // Paginate
        int total = filtered.size();
        int start = page * size;
        int end = Math.min(start + size, total);

        List<IfcMepElementInfo> pageContent = start < total ?
            filtered.subList(start, end) : List.of();

        Map<String, Object> response = new HashMap<>();
        response.put("content", pageContent);
        response.put("page", page);
        response.put("size", size);
        response.put("totalElements", total);
        response.put("totalPages", (int) Math.ceil((double) total / size));

        return ResponseEntity.ok(response);
    }

    /**
     * Get materials from a completed job.
     *
     * GET /api/ifc/{jobId}/materials
     *
     * @param jobId Job ID
     * @return Materials list with usage counts
     */
    @GetMapping("/{jobId}/materials")
    public ResponseEntity<?> getJobMaterials(@PathVariable String jobId) {
        IfcProcessingJob job = ifcProcessingService.getJob(jobId);

        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        if (job.getStatus() != IfcProcessingService.IfcProcessingStatus.COMPLETED) {
            Map<String, String> response = new HashMap<>();
            response.put("status", job.getStatus().name());
            response.put("message", "Töö pole veel lõpetatud");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }

        return ResponseEntity.ok(job.getResult().materials());
    }

    /**
     * Get spaces (rooms) from a completed job.
     *
     * GET /api/ifc/{jobId}/spaces
     *
     * @param jobId Job ID
     * @return Spaces list
     */
    @GetMapping("/{jobId}/spaces")
    public ResponseEntity<?> getJobSpaces(@PathVariable String jobId) {
        IfcProcessingJob job = ifcProcessingService.getJob(jobId);

        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        if (job.getStatus() != IfcProcessingService.IfcProcessingStatus.COMPLETED) {
            Map<String, String> response = new HashMap<>();
            response.put("status", job.getStatus().name());
            response.put("message", "Töö pole veel lõpetatud");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }

        return ResponseEntity.ok(job.getResult().spaces());
    }

    /**
     * Health check endpoint.
     *
     * GET /api/ifc/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
