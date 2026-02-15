package com.buildquote.service;

import com.buildquote.entity.GooglePlacesCache;
import com.buildquote.entity.Supplier;
import com.buildquote.repository.GooglePlacesCacheRepository;
import com.buildquote.repository.SupplierRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupplierSearchService {

    private final GooglePlacesService googlePlacesService;
    private final GooglePlacesCacheRepository cacheRepository;
    private final SupplierRepository supplierRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Cache validity: 7 days
    private static final int CACHE_DAYS = 7;

    // Thread pool for parallel searches
    private final ExecutorService searchExecutor = Executors.newFixedThreadPool(6);

    // Search terms for each category
    private static final Map<String, String> SEARCH_TERMS = new LinkedHashMap<>();
    static {
        SEARCH_TERMS.put("GENERAL_CONSTRUCTION", "ehitusfirma");
        SEARCH_TERMS.put("ELECTRICAL", "elektrik");
        SEARCH_TERMS.put("PLUMBING", "torumees sanitaar");
        SEARCH_TERMS.put("TILING", "plaatija");
        SEARCH_TERMS.put("FINISHING", "viimistlustööd remont");
        SEARCH_TERMS.put("ROOFING", "katusemeister");
        SEARCH_TERMS.put("FACADE", "fassaaditööd");
        SEARCH_TERMS.put("LANDSCAPING", "maastikuehitus haljastus");
        SEARCH_TERMS.put("WINDOWS_DOORS", "aknapaigaldus");
        SEARCH_TERMS.put("HVAC", "küttesüsteemid ventilatsioon");
        SEARCH_TERMS.put("FLOORING", "põrandatööd parketi laminaat");
        SEARCH_TERMS.put("DEMOLITION", "lammutustööd");
    }

    /**
     * Search for suppliers in a category and location.
     * Uses cache if available (searched within last 7 days).
     */
    public List<GooglePlacesService.PlaceResult> searchCached(String category, String location) {
        LocalDateTime cacheMinDate = LocalDateTime.now().minusDays(CACHE_DAYS);

        // Check cache first
        Optional<GooglePlacesCache> cached = cacheRepository.findValidCache(category, location, cacheMinDate);
        if (cached.isPresent()) {
            GooglePlacesCache cache = cached.get();
            log.debug("Cache hit for {}:{} (searched {} ago)",
                category, location, java.time.Duration.between(cache.getSearchedAt(), LocalDateTime.now()));

            // Update usage stats
            cacheRepository.incrementUseCount(cache.getId(), LocalDateTime.now());

            // Parse and return cached results
            try {
                return objectMapper.readValue(cache.getResultsJson(),
                    new TypeReference<List<GooglePlacesService.PlaceResult>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse cached results, fetching fresh: {}", e.getMessage());
            }
        }

        // Cache miss - fetch from Google Places
        String searchTerm = SEARCH_TERMS.getOrDefault(category, category.toLowerCase());
        List<GooglePlacesService.PlaceResult> results = googlePlacesService.searchPlaces(searchTerm, location);

        // Store in cache
        saveToCache(category, location, results);

        return results;
    }

    @Transactional
    protected void saveToCache(String category, String location, List<GooglePlacesService.PlaceResult> results) {
        try {
            GooglePlacesCache cache = new GooglePlacesCache();
            cache.setCategory(category);
            cache.setLocation(location);
            cache.setResultsJson(objectMapper.writeValueAsString(results));
            cache.setResultCount(results.size());
            cache.setSearchedAt(LocalDateTime.now());
            cache.setLastUsedAt(LocalDateTime.now());
            cache.setUseCount(1);
            cacheRepository.save(cache);
            log.debug("Cached {} results for {}:{}", results.size(), category, location);
        } catch (Exception e) {
            log.warn("Failed to cache results: {}", e.getMessage());
        }
    }

    /**
     * Search multiple categories in PARALLEL for a given location.
     * Returns combined results as they complete.
     */
    public List<GooglePlacesService.PlaceResult> searchCategoriesParallel(List<String> categories, String location) {
        List<CompletableFuture<List<GooglePlacesService.PlaceResult>>> futures = new ArrayList<>();

        for (String category : categories) {
            CompletableFuture<List<GooglePlacesService.PlaceResult>> future =
                CompletableFuture.supplyAsync(() -> searchCached(category, location), searchExecutor);
            futures.add(future);
        }

        // Wait for all to complete
        CompletableFuture<Void> allDone = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );

        try {
            allDone.get(30, TimeUnit.SECONDS); // 30 second timeout
        } catch (TimeoutException e) {
            log.warn("Parallel search timed out after 30 seconds");
        } catch (Exception e) {
            log.error("Error in parallel search: {}", e.getMessage());
        }

        // Collect results
        List<GooglePlacesService.PlaceResult> allResults = new ArrayList<>();
        for (CompletableFuture<List<GooglePlacesService.PlaceResult>> future : futures) {
            try {
                if (future.isDone() && !future.isCompletedExceptionally()) {
                    allResults.addAll(future.get());
                }
            } catch (Exception e) {
                log.debug("Failed to get result from future: {}", e.getMessage());
            }
        }

        return allResults;
    }

    /**
     * Stream search results via SSE as they are found.
     * Results appear in real-time instead of waiting for all searches.
     */
    public void searchWithSSE(List<String> categories, String location, SseEmitter emitter) {
        AtomicInteger completed = new AtomicInteger(0);
        int total = categories.size();

        for (String category : categories) {
            CompletableFuture.supplyAsync(() -> searchCached(category, location), searchExecutor)
                .thenAccept(results -> {
                    try {
                        // Send each result as it's found
                        for (GooglePlacesService.PlaceResult result : results) {
                            Map<String, Object> event = new HashMap<>();
                            event.put("type", "supplier");
                            event.put("category", category);
                            event.put("data", result);
                            emitter.send(SseEmitter.event()
                                .name("supplier")
                                .data(event));
                        }

                        // Send category completion event
                        int done = completed.incrementAndGet();
                        emitter.send(SseEmitter.event()
                            .name("progress")
                            .data(Map.of(
                                "category", category,
                                "found", results.size(),
                                "completed", done,
                                "total", total
                            )));

                        // If all done, complete the emitter
                        if (done == total) {
                            emitter.send(SseEmitter.event()
                                .name("complete")
                                .data(Map.of("totalCategories", total)));
                            emitter.complete();
                        }
                    } catch (IOException e) {
                        log.debug("SSE send failed: {}", e.getMessage());
                    }
                })
                .exceptionally(ex -> {
                    log.error("Search failed for {}: {}", category, ex.getMessage());
                    int done = completed.incrementAndGet();
                    if (done == total) {
                        try {
                            emitter.send(SseEmitter.event()
                                .name("complete")
                                .data(Map.of("totalCategories", total)));
                            emitter.complete();
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    }
                    return null;
                });
        }
    }

    /**
     * Get count of suppliers for a category/location.
     * First checks database, then falls back to cached Google Places results.
     */
    public int getSupplierCount(String category, String location) {
        // First check database for existing suppliers
        int dbCount = supplierRepository.countByCategoryAndCity(category, location);
        if (dbCount > 0) {
            return dbCount;
        }

        // Fallback to category-wide count
        dbCount = supplierRepository.countByCategory(category);
        if (dbCount > 0) {
            return dbCount;
        }

        // Check cache for Google Places results
        LocalDateTime cacheMinDate = LocalDateTime.now().minusDays(CACHE_DAYS);
        Optional<GooglePlacesCache> cached = cacheRepository.findValidCache(category, location, cacheMinDate);
        if (cached.isPresent()) {
            return cached.get().getResultCount();
        }

        // No data available
        return 0;
    }

    /**
     * Cleanup old cache entries (older than 14 days).
     */
    @Transactional
    public int cleanupOldCache() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(14);
        int deleted = cacheRepository.deleteOldEntries(cutoff);
        log.info("Cleaned up {} old cache entries", deleted);
        return deleted;
    }

    /**
     * Get cache statistics.
     */
    public Map<String, Object> getCacheStats() {
        LocalDateTime minDate = LocalDateTime.now().minusDays(CACHE_DAYS);
        long validEntries = cacheRepository.countValidEntries(minDate);

        Map<String, Object> stats = new HashMap<>();
        stats.put("validCacheEntries", validEntries);
        stats.put("cacheDays", CACHE_DAYS);
        stats.put("totalCategories", SEARCH_TERMS.size());
        return stats;
    }
}
