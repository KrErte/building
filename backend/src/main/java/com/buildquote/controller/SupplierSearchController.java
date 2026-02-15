package com.buildquote.controller;

import com.buildquote.service.CacheWarmupService;
import com.buildquote.service.GooglePlacesService;
import com.buildquote.service.SupplierSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/suppliers")
@CrossOrigin
@RequiredArgsConstructor
@Slf4j
public class SupplierSearchController {

    private final SupplierSearchService supplierSearchService;
    private final CacheWarmupService cacheWarmupService;

    /**
     * SSE endpoint for streaming supplier search results.
     * Results appear as they are found instead of waiting for all.
     *
     * Usage: EventSource('/api/suppliers/search/stream?categories=TILING,ELECTRICAL&location=Tallinn')
     */
    @GetMapping(value = "/search/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSearch(
            @RequestParam String categories,
            @RequestParam(defaultValue = "Tallinn") String location
    ) {
        log.info("SSE search started: categories={}, location={}", categories, location);

        SseEmitter emitter = new SseEmitter(60000L); // 60 second timeout

        List<String> categoryList = Arrays.asList(categories.split(","));

        // Send initial event
        try {
            emitter.send(SseEmitter.event()
                .name("start")
                .data(Map.of(
                    "categories", categoryList,
                    "location", location,
                    "totalCategories", categoryList.size()
                )));
        } catch (Exception e) {
            log.error("Failed to send start event: {}", e.getMessage());
        }

        // Start async search
        supplierSearchService.searchWithSSE(categoryList, location, emitter);

        emitter.onCompletion(() -> log.debug("SSE completed"));
        emitter.onTimeout(() -> {
            log.warn("SSE timed out");
            emitter.complete();
        });
        emitter.onError(ex -> log.error("SSE error: {}", ex.getMessage()));

        return emitter;
    }

    /**
     * Regular search endpoint (waits for all results).
     * Faster than sequential due to parallel execution.
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam String categories,
            @RequestParam(defaultValue = "Tallinn") String location
    ) {
        log.info("Parallel search: categories={}, location={}", categories, location);

        List<String> categoryList = Arrays.asList(categories.split(","));
        long start = System.currentTimeMillis();

        List<GooglePlacesService.PlaceResult> results =
            supplierSearchService.searchCategoriesParallel(categoryList, location);

        long duration = System.currentTimeMillis() - start;
        log.info("Parallel search completed in {}ms, found {} results", duration, results.size());

        return ResponseEntity.ok(Map.of(
            "results", results,
            "count", results.size(),
            "categories", categoryList,
            "location", location,
            "durationMs", duration
        ));
    }

    /**
     * Get supplier count for a category/location (fast, uses cache).
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> getCount(
            @RequestParam String category,
            @RequestParam(defaultValue = "Tallinn") String location
    ) {
        int count = supplierSearchService.getSupplierCount(category, location);
        return ResponseEntity.ok(Map.of(
            "category", category,
            "location", location,
            "count", count
        ));
    }

    /**
     * Get cache statistics.
     */
    @GetMapping("/cache/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        return ResponseEntity.ok(supplierSearchService.getCacheStats());
    }

    /**
     * Trigger manual cache warmup.
     */
    @PostMapping("/cache/warmup")
    public ResponseEntity<Map<String, Object>> triggerWarmup(
            @RequestParam(defaultValue = "false") boolean extended
    ) {
        try {
            cacheWarmupService.triggerWarmup(extended);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Cache warmup started",
                "extended", extended
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Get warmup status.
     */
    @GetMapping("/cache/warmup/status")
    public ResponseEntity<Map<String, Object>> getWarmupStatus() {
        return ResponseEntity.ok(cacheWarmupService.getWarmupStatus());
    }

    /**
     * Cleanup old cache entries manually.
     */
    @DeleteMapping("/cache/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupCache() {
        int deleted = supplierSearchService.cleanupOldCache();
        return ResponseEntity.ok(Map.of(
            "deleted", deleted,
            "message", "Old cache entries cleaned up"
        ));
    }
}
