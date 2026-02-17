package com.buildquote.service;

import com.buildquote.entity.GooglePlacesCache;
import com.buildquote.entity.Supplier;
import com.buildquote.repository.GooglePlacesCacheRepository;
import com.buildquote.repository.SupplierRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
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
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory cache of supplier counts (refreshed every 5 minutes)
    private volatile Map<String, Integer> categoryCountCache = new ConcurrentHashMap<>();
    private volatile Map<String, Integer> cityCountCache = new ConcurrentHashMap<>();
    private volatile long totalSupplierCount = 0;
    private volatile long crawlerCompanyCount = 0;

    // EMTAK code mappings to our categories
    // 41xxx = Buildings, 42xxx = Civil engineering, 43xxx = Specialized construction
    private static final Map<String, List<String>> CATEGORY_TO_EMTAK = Map.ofEntries(
        Map.entry("GENERAL_CONSTRUCTION", List.of("41", "411", "4110", "412", "4120")),
        Map.entry("ELECTRICAL", List.of("4321", "43210")),
        Map.entry("PLUMBING", List.of("4322", "43221", "43222")),
        Map.entry("TILING", List.of("4333", "43330")),
        Map.entry("FINISHING", List.of("4334", "43341", "43342", "4339", "43391")),
        Map.entry("ROOFING", List.of("4391", "43910")),
        Map.entry("FACADE", List.of("4399", "43991")),
        Map.entry("LANDSCAPING", List.of("4333", "43331")),
        Map.entry("WINDOWS_DOORS", List.of("4332", "43320")),
        Map.entry("HVAC", List.of("4322", "43221", "43222", "43223")),
        Map.entry("FLOORING", List.of("4333", "43332", "43339")),
        Map.entry("DEMOLITION", List.of("4311", "43110"))
    );

    // Cache validity: 7 days
    private static final int CACHE_DAYS = 7;

    // Thread pool for parallel searches
    private final ExecutorService searchExecutor = Executors.newFixedThreadPool(6);

    /**
     * Initialize supplier count cache at startup.
     */
    @PostConstruct
    public void initCountCache() {
        refreshCountCache();
    }

    /**
     * Refresh count cache every 5 minutes.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void refreshCountCache() {
        try {
            long start = System.currentTimeMillis();

            // Load category counts from suppliers
            Map<String, Integer> newCategoryCache = new ConcurrentHashMap<>();
            for (Object[] row : supplierRepository.countAllByCategory()) {
                String categories = (String) row[0];
                int count = ((Number) row[1]).intValue();
                if (categories != null) {
                    for (String cat : categories.split(",")) {
                        cat = cat.trim();
                        newCategoryCache.merge(cat, count, Integer::sum);
                    }
                }
            }

            // Add counts from crawler.company by EMTAK codes (single optimized query)
            try {
                Map<String, Integer> emtakCounts = countAllCrawlerCompaniesByEmtak();
                for (Map.Entry<String, Integer> entry : emtakCounts.entrySet()) {
                    newCategoryCache.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
            } catch (Exception e) {
                log.debug("Could not query crawler.company EMTAK counts: {}", e.getMessage());
            }
            categoryCountCache = newCategoryCache;

            // Load city counts from suppliers
            Map<String, Integer> newCityCache = new ConcurrentHashMap<>();
            for (Object[] row : supplierRepository.countAllByCity()) {
                String city = (String) row[0];
                int count = ((Number) row[1]).intValue();
                if (city != null) {
                    newCityCache.put(city.toUpperCase(), count);
                }
            }

            // Add city counts from crawler.company
            try {
                List<Map<String, Object>> crawlerCities = jdbcTemplate.queryForList(
                    "SELECT city, COUNT(*) as cnt FROM crawler.company WHERE city IS NOT NULL GROUP BY city");
                for (Map<String, Object> row : crawlerCities) {
                    String city = (String) row.get("city");
                    int count = ((Number) row.get("cnt")).intValue();
                    if (city != null) {
                        newCityCache.merge(city.toUpperCase(), count, Integer::sum);
                    }
                }
            } catch (Exception e) {
                log.debug("Could not query crawler.company cities: {}", e.getMessage());
            }
            cityCountCache = newCityCache;

            // Total counts
            totalSupplierCount = supplierRepository.countAll();
            try {
                crawlerCompanyCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM crawler.company", Long.class);
            } catch (Exception e) {
                crawlerCompanyCount = 0;
            }

            long duration = System.currentTimeMillis() - start;
            log.info("Supplier count cache refreshed in {}ms: {} categories, {} cities, {} suppliers + {} crawler companies",
                duration, categoryCountCache.size(), cityCountCache.size(), totalSupplierCount, crawlerCompanyCount);

        } catch (Exception e) {
            log.warn("Failed to refresh count cache: {}", e.getMessage());
        }
    }

    /**
     * Count all crawler companies by EMTAK prefix in a single efficient query.
     * Uses EMTAK code prefixes: 41=buildings, 42=civil, 43=specialized
     */
    private Map<String, Integer> countAllCrawlerCompaniesByEmtak() {
        Map<String, Integer> counts = new HashMap<>();

        // Single query to count by first 2 digits of EMTAK codes
        String sql = """
            SELECT
                CASE
                    WHEN ec LIKE '411%' OR ec LIKE '412%' THEN 'GENERAL_CONSTRUCTION'
                    WHEN ec LIKE '4321%' THEN 'ELECTRICAL'
                    WHEN ec LIKE '4322%' THEN 'PLUMBING'
                    WHEN ec LIKE '4333%' THEN 'TILING'
                    WHEN ec LIKE '4334%' OR ec LIKE '4339%' THEN 'FINISHING'
                    WHEN ec LIKE '4391%' THEN 'ROOFING'
                    WHEN ec LIKE '4399%' THEN 'FACADE'
                    WHEN ec LIKE '43331%' THEN 'LANDSCAPING'
                    WHEN ec LIKE '4332%' THEN 'WINDOWS_DOORS'
                    WHEN ec LIKE '43221%' OR ec LIKE '43222%' OR ec LIKE '43223%' THEN 'HVAC'
                    WHEN ec LIKE '43332%' OR ec LIKE '43339%' THEN 'FLOORING'
                    WHEN ec LIKE '4311%' THEN 'DEMOLITION'
                    ELSE NULL
                END as category,
                COUNT(DISTINCT c.id) as cnt
            FROM crawler.company c, unnest(c.emtak_codes) ec
            GROUP BY 1
            HAVING COUNT(DISTINCT c.id) > 0
            """;

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : rows) {
                String category = (String) row.get("category");
                if (category != null) {
                    int count = ((Number) row.get("cnt")).intValue();
                    counts.merge(category, count, Integer::sum);
                }
            }
        } catch (Exception e) {
            log.debug("EMTAK count query failed: {}", e.getMessage());
        }

        return counts;
    }

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
     * Uses in-memory cache for instant response (no DB queries).
     */
    public int getSupplierCount(String category, String location) {
        // Use in-memory cache (instant lookup)
        int categoryCount = categoryCountCache.getOrDefault(category, 0);

        if (categoryCount > 0) {
            // If we have city data, estimate based on city distribution
            int cityCount = cityCountCache.getOrDefault(location.toUpperCase(), 0);
            if (cityCount > 0 && totalSupplierCount > 0) {
                // Rough estimate: category suppliers in this city
                double cityRatio = (double) cityCount / totalSupplierCount;
                int estimated = (int) Math.max(categoryCount * cityRatio, 5);
                return Math.min(estimated, categoryCount);
            }
            return categoryCount;
        }

        // Fallback: check Google Places cache
        LocalDateTime cacheMinDate = LocalDateTime.now().minusDays(CACHE_DAYS);
        Optional<GooglePlacesCache> cached = cacheRepository.findValidCache(category, location, cacheMinDate);
        if (cached.isPresent()) {
            return cached.get().getResultCount();
        }

        // Default minimum
        return 10;
    }

    /**
     * Get total supplier count (instant from cache).
     */
    public long getTotalSupplierCount() {
        return totalSupplierCount + crawlerCompanyCount;
    }

    /**
     * Get crawler company count only.
     */
    public long getCrawlerCompanyCount() {
        return crawlerCompanyCount;
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
