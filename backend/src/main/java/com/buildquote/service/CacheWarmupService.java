package com.buildquote.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheWarmupService {

    private final SupplierSearchService supplierSearchService;
    private final GooglePlacesService googlePlacesService;

    // Top 20 categories (all categories)
    private static final List<String> TOP_CATEGORIES = Arrays.asList(
        "GENERAL_CONSTRUCTION",
        "ELECTRICAL",
        "PLUMBING",
        "TILING",
        "FINISHING",
        "ROOFING",
        "FACADE",
        "LANDSCAPING",
        "WINDOWS_DOORS",
        "HVAC",
        "FLOORING",
        "DEMOLITION"
    );

    // Top 5 cities by population/activity
    private static final List<String> TOP_CITIES = Arrays.asList(
        "Tallinn",
        "Tartu",
        "Pärnu",
        "Narva",
        "Rakvere"
    );

    // Additional cities for extended warmup
    private static final List<String> ALL_CITIES = Arrays.asList(
        "Tallinn", "Tartu", "Pärnu", "Narva", "Rakvere",
        "Viljandi", "Kuressaare", "Haapsalu", "Jõhvi", "Võru",
        "Paide", "Valga", "Rapla", "Põlva"
    );

    private final ExecutorService warmupExecutor = Executors.newFixedThreadPool(4);

    /**
     * Nightly cache warmup - runs at 3:00 AM every day.
     * Pre-warms top 20 categories × top 5 cities = 60 searches.
     */
    @Scheduled(cron = "0 0 3 * * *") // 3:00 AM daily
    public void nightlyWarmup() {
        log.info("Starting nightly cache warmup at {}", LocalDateTime.now());

        if (!googlePlacesService.isConfigured()) {
            log.warn("Google Places API not configured, skipping warmup");
            return;
        }

        warmupCache(TOP_CATEGORIES, TOP_CITIES);
    }

    /**
     * Weekly extended warmup - runs Sunday at 4:00 AM.
     * Pre-warms all categories × all cities.
     */
    @Scheduled(cron = "0 0 4 * * SUN") // 4:00 AM every Sunday
    public void weeklyExtendedWarmup() {
        log.info("Starting weekly extended cache warmup at {}", LocalDateTime.now());

        if (!googlePlacesService.isConfigured()) {
            log.warn("Google Places API not configured, skipping warmup");
            return;
        }

        warmupCache(TOP_CATEGORIES, ALL_CITIES);
    }

    /**
     * Manual warmup trigger.
     */
    public void triggerWarmup(boolean extended) {
        log.info("Manual cache warmup triggered (extended: {})", extended);

        if (!googlePlacesService.isConfigured()) {
            throw new IllegalStateException("Google Places API not configured");
        }

        List<String> cities = extended ? ALL_CITIES : TOP_CITIES;
        warmupCache(TOP_CATEGORIES, cities);
    }

    /**
     * Warm up cache for given categories and cities.
     * Uses parallel execution with rate limiting.
     */
    private void warmupCache(List<String> categories, List<String> cities) {
        int totalSearches = categories.size() * cities.size();
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger cached = new AtomicInteger(0);
        LocalDateTime startTime = LocalDateTime.now();

        log.info("Warming up cache: {} categories × {} cities = {} searches",
            categories.size(), cities.size(), totalSearches);

        // Create all futures
        List<CompletableFuture<Void>> futures = new java.util.ArrayList<>();

        for (String category : categories) {
            for (String city : cities) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        // This will fetch and cache if not already cached
                        var results = supplierSearchService.searchCached(category, city);
                        int done = completed.incrementAndGet();
                        cached.addAndGet(results.size());

                        if (done % 10 == 0 || done == totalSearches) {
                            log.info("Warmup progress: {}/{} ({} results cached)",
                                done, totalSearches, cached.get());
                        }

                        // Rate limit: 500ms between API calls
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        log.warn("Warmup failed for {}:{}: {}", category, city, e.getMessage());
                        completed.incrementAndGet();
                    }
                }, warmupExecutor);

                futures.add(future);
            }
        }

        // Wait for all to complete with timeout
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(30, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Cache warmup interrupted: {}", e.getMessage());
        }

        LocalDateTime endTime = LocalDateTime.now();
        long durationSeconds = java.time.Duration.between(startTime, endTime).getSeconds();

        log.info("Cache warmup completed: {} searches in {} seconds, {} total results cached",
            completed.get(), durationSeconds, cached.get());

        // Cleanup old entries after warmup
        supplierSearchService.cleanupOldCache();
    }

    /**
     * Get warmup status.
     */
    public java.util.Map<String, Object> getWarmupStatus() {
        java.util.Map<String, Object> status = new java.util.HashMap<>();
        status.put("topCategories", TOP_CATEGORIES.size());
        status.put("topCities", TOP_CITIES.size());
        status.put("allCities", ALL_CITIES.size());
        status.put("dailyWarmupSearches", TOP_CATEGORIES.size() * TOP_CITIES.size());
        status.put("weeklyWarmupSearches", TOP_CATEGORIES.size() * ALL_CITIES.size());
        status.put("cacheStats", supplierSearchService.getCacheStats());
        return status;
    }
}
