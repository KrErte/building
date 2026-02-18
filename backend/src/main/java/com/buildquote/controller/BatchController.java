package com.buildquote.controller;

import com.buildquote.service.BatchHarvestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/batch")
public class BatchController {

    private static final Logger log = LoggerFactory.getLogger(BatchController.class);

    private final BatchHarvestService batchHarvestService;

    public BatchController(BatchHarvestService batchHarvestService) {
        this.batchHarvestService = batchHarvestService;
    }

    /**
     * Run full harvest: Google Places + filtering + website scraping + deduplication
     */
    @Secured("ROLE_ADMIN")
    @PostMapping("/harvest/full")
    public ResponseEntity<Map<String, Object>> runFullHarvest() {
        log.info("Starting FULL batch harvest (Google Places + scraping + dedup)...");
        Map<String, Object> result = batchHarvestService.runFullHarvest();
        return ResponseEntity.ok(result);
    }

    /**
     * Run Google Places harvest only
     */
    @Secured("ROLE_ADMIN")
    @PostMapping("/harvest/google")
    public ResponseEntity<Map<String, Object>> runGoogleHarvest() {
        log.info("Starting Google Places harvest...");
        Map<String, Object> result = batchHarvestService.runGooglePlacesOnly();
        return ResponseEntity.ok(result);
    }

    /**
     * Run PARALLEL Google Places harvest - 6x faster!
     */
    @Secured("ROLE_ADMIN")
    @PostMapping("/harvest/parallel")
    public ResponseEntity<Map<String, Object>> runParallelHarvest() {
        log.info("Starting PARALLEL Google Places harvest (6x faster)...");
        Map<String, Object> result = batchHarvestService.runParallelHarvest();
        return ResponseEntity.ok(result);
    }

    /**
     * Run email scraping only
     */
    @Secured("ROLE_ADMIN")
    @PostMapping("/harvest/scrape-emails")
    public ResponseEntity<Map<String, Object>> runEmailScrape() {
        log.info("Starting email scraping...");
        Map<String, Object> result = batchHarvestService.runEmailScrape();
        return ResponseEntity.ok(result);
    }

    /**
     * Run deduplication only
     */
    @Secured("ROLE_ADMIN")
    @PostMapping("/harvest/deduplicate")
    public ResponseEntity<Map<String, Object>> runDeduplicate() {
        log.info("Starting deduplication...");
        Map<String, Object> result = batchHarvestService.runDeduplicate();
        return ResponseEntity.ok(result);
    }

    /**
     * Legacy endpoint - calls full harvest
     */
    @Secured("ROLE_ADMIN")
    @PostMapping("/harvest")
    public ResponseEntity<Map<String, Object>> runHarvest() {
        log.info("Starting batch supplier harvest...");
        Map<String, Object> result = batchHarvestService.runFullHarvest();
        return ResponseEntity.ok(result);
    }

    /**
     * Get current status
     */
    @Secured("ROLE_ADMIN")
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(batchHarvestService.getStatus());
    }

    /**
     * Get supplier statistics (public - used by landing page)
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(batchHarvestService.getStats());
    }
}
