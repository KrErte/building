package com.buildquote.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ContactCrawlerService {

    private static final Logger log = LoggerFactory.getLogger(ContactCrawlerService.class);

    private final GooglePlacesService googlePlacesService;
    private final JdbcTemplate jdbcTemplate;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger foundCount = new AtomicInteger(0);
    private final AtomicInteger totalCount = new AtomicInteger(0);

    public ContactCrawlerService(GooglePlacesService googlePlacesService, JdbcTemplate jdbcTemplate) {
        this.googlePlacesService = googlePlacesService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getStatus() {
        return Map.of(
            "isRunning", isRunning.get(),
            "processed", processedCount.get(),
            "found", foundCount.get(),
            "total", totalCount.get(),
            "googlePlacesConfigured", googlePlacesService.isConfigured()
        );
    }

    public String startCrawling(int limit) {
        if (!googlePlacesService.isConfigured()) {
            return "Google Places API key not configured";
        }

        if (isRunning.get()) {
            return "Crawler is already running";
        }

        crawlAsync(limit);
        return "Crawler started for " + limit + " companies";
    }

    public void stopCrawling() {
        isRunning.set(false);
    }

    @Async
    public void crawlAsync(int limit) {
        isRunning.set(true);
        processedCount.set(0);
        foundCount.set(0);

        try {
            // Get companies without phone from crawler.company
            // phone is TEXT[] - empty array {} has array_length NULL
            String sql = """
                SELECT id, legal_name, city
                FROM crawler.company
                WHERE phone IS NULL OR phone = '{}' OR array_length(phone, 1) IS NULL
                LIMIT ?
                """;

            List<Map<String, Object>> companies = jdbcTemplate.queryForList(sql, limit);
            totalCount.set(companies.size());

            log.info("Starting contact crawl for {} companies", companies.size());

            for (Map<String, Object> company : companies) {
                if (!isRunning.get()) {
                    log.info("Crawler stopped by user");
                    break;
                }

                String id = company.get("id").toString();
                String name = (String) company.get("legal_name");
                String city = (String) company.get("city");

                if (name == null || name.isEmpty()) {
                    processedCount.incrementAndGet();
                    continue;
                }

                try {
                    // Search Google Places
                    List<GooglePlacesService.PlaceResult> results =
                        googlePlacesService.searchPlaces(name, city != null ? city : "Estonia");

                    if (!results.isEmpty()) {
                        // Find best match (first result usually best)
                        GooglePlacesService.PlaceResult best = results.get(0);

                        // Update database if we found contact info
                        if (best.phone != null || best.website != null) {
                            updateCompanyContact(id, best.phone, best.website);
                            foundCount.incrementAndGet();
                            log.info("Found contact for {}: phone={}, website={}",
                                name, best.phone, best.website);
                        }
                    }

                    // Rate limiting - Google Places has quotas
                    Thread.sleep(200); // 5 requests per second max

                } catch (Exception e) {
                    log.error("Error processing company {}: {}", name, e.getMessage());
                }

                processedCount.incrementAndGet();

                if (processedCount.get() % 100 == 0) {
                    log.info("Progress: {}/{}, found: {}",
                        processedCount.get(), totalCount.get(), foundCount.get());
                }
            }

        } catch (Exception e) {
            log.error("Crawler error: {}", e.getMessage(), e);
        } finally {
            isRunning.set(false);
            log.info("Crawling finished. Processed: {}, Found: {}",
                processedCount.get(), foundCount.get());
        }
    }

    private void updateCompanyContact(String id, String phone, String website) {
        String sql = """
            UPDATE crawler.company
            SET phone = CASE WHEN ? IS NOT NULL THEN ARRAY[?] ELSE phone END,
                website = COALESCE(?, website)
            WHERE id = ?::uuid
            """;

        jdbcTemplate.update(sql, phone, phone, website, id);
    }
}
