package com.buildquote.service;

import com.buildquote.entity.Supplier;
import com.buildquote.repository.SupplierRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class BatchHarvestService {

    private static final Logger log = LoggerFactory.getLogger(BatchHarvestService.class);

    // 12 construction categories with Estonian search terms
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

    // 14 Estonian cities
    private static final String[] CITIES = {
        "Tallinn", "Tartu", "Pärnu", "Narva", "Rakvere",
        "Viljandi", "Kuressaare", "Haapsalu", "Jõhvi", "Võru",
        "Paide", "Valga", "Rapla", "Põlva"
    };

    // Names to filter out (non-construction businesses)
    private static final String[] BLACKLIST_NAMES = {
        "Swedbank", "SEB", "LHV", "Luminor", "Coop", "Maxima", "Selver",
        "Circle K", "Neste", "Alexela", "Apteek", "Rimi", "Prisma",
        "Bolt", "Wolt", "Telia", "Elisa", "Tele2", "McDonald", "Hesburger",
        "Kaubamaja", "Stockmann", "H&M", "Zara", "Sportland"
    };

    // Email regex pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}");

    // Phone regex pattern (Estonian format)
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "(?:\\+372|372)?\\s*[0-9]{3,4}\\s*[0-9]{3,4}");

    private final GooglePlacesService googlePlacesService;
    private final SupplierRepository supplierRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService parallelExecutor = Executors.newFixedThreadPool(6);

    // State tracking
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicInteger totalSearches = new AtomicInteger(0);
    private final AtomicInteger completedSearches = new AtomicInteger(0);
    private final AtomicInteger foundCount = new AtomicInteger(0);
    private final AtomicInteger newCount = new AtomicInteger(0);
    private final AtomicInteger duplicateCount = new AtomicInteger(0);
    private final AtomicInteger filteredCount = new AtomicInteger(0);
    private final AtomicInteger emailsScraped = new AtomicInteger(0);
    private final AtomicInteger phonesScraped = new AtomicInteger(0);
    private final Set<String> seenPlaceIds = ConcurrentHashMap.newKeySet();
    private final Set<String> seenNames = ConcurrentHashMap.newKeySet();
    private String currentStatus = "IDLE";
    private String currentTask = "";
    private String lastError = null;
    private LocalDateTime startedAt = null;
    private LocalDateTime completedAt = null;

    public BatchHarvestService(GooglePlacesService googlePlacesService, SupplierRepository supplierRepository, JdbcTemplate jdbcTemplate) {
        this.googlePlacesService = googlePlacesService;
        this.supplierRepository = supplierRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("isRunning", isRunning.get());
        status.put("status", currentStatus);
        status.put("currentTask", currentTask);
        status.put("totalSearches", totalSearches.get());
        status.put("completedSearches", completedSearches.get());
        status.put("found", foundCount.get());
        status.put("new", newCount.get());
        status.put("duplicates", duplicateCount.get());
        status.put("filtered", filteredCount.get());
        status.put("emailsScraped", emailsScraped.get());
        status.put("phonesScraped", phonesScraped.get());
        status.put("startedAt", startedAt);
        status.put("completedAt", completedAt);
        status.put("lastError", lastError);
        status.put("totalSuppliers", supplierRepository.count());
        return status;
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();

        List<Supplier> allSuppliers = supplierRepository.findAll();
        long supplierCount = allSuppliers.size();

        // Include crawler.company count (31,000+ Estonian construction companies)
        long crawlerCount = 0;
        try {
            crawlerCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crawler.company", Long.class);
        } catch (Exception e) {
            // crawler schema may not exist
        }

        long total = supplierCount + crawlerCount;
        stats.put("totalCompanies", total);
        stats.put("suppliersUnified", supplierCount);
        stats.put("crawlerCompanies", crawlerCount);

        // Count with email
        long withEmail = allSuppliers.stream()
            .filter(s -> s.getEmail() != null && !s.getEmail().isEmpty())
            .count();
        stats.put("withEmail", withEmail);

        // Count with phone
        long withPhone = allSuppliers.stream()
            .filter(s -> s.getPhone() != null && !s.getPhone().isEmpty())
            .count();
        stats.put("withPhone", withPhone);

        // Count with website
        long withWebsite = allSuppliers.stream()
            .filter(s -> s.getWebsite() != null && !s.getWebsite().isEmpty())
            .count();
        stats.put("withWebsite", withWebsite);

        // By category (count suppliers that have each category in their categories array)
        Map<String, Long> byCategory = new HashMap<>();
        for (String cat : SEARCH_TERMS.keySet()) {
            long count = allSuppliers.stream()
                .filter(s -> s.getCategories() != null && Arrays.asList(s.getCategories()).contains(cat))
                .count();
            byCategory.put(cat, count);
        }
        stats.put("byCategory", byCategory);

        // By city
        Map<String, Long> byCity = allSuppliers.stream()
            .filter(s -> s.getCity() != null && !s.getCity().isEmpty())
            .collect(Collectors.groupingBy(Supplier::getCity, Collectors.counting()));
        stats.put("byCity", byCity);

        // By source
        Map<String, Long> bySource = allSuppliers.stream()
            .filter(s -> s.getSource() != null && !s.getSource().isEmpty())
            .collect(Collectors.groupingBy(Supplier::getSource, Collectors.counting()));
        stats.put("bySource", bySource);

        return stats;
    }

    @Transactional
    public Map<String, Object> runFullHarvest() {
        if (isRunning.get()) {
            return Map.of("success", false, "error", "Harvest is already running", "status", getStatus());
        }

        if (!googlePlacesService.isConfigured()) {
            return Map.of("success", false, "error", "Google Places API key not configured");
        }

        resetCounters();
        isRunning.set(true);
        startedAt = LocalDateTime.now();

        try {
            // Step 1: Google Places harvest
            runGooglePlacesHarvest();

            // Step 2: Filter non-construction
            filterNonConstruction();

            // Step 3: Scrape websites for emails
            scrapeWebsitesForContacts();

            // Step 4: Deduplicate
            deduplicateSuppliers();

            currentStatus = "COMPLETED";
            completedAt = LocalDateTime.now();

            logFinalStats();

        } catch (Exception e) {
            currentStatus = "FAILED";
            lastError = e.getMessage();
            log.error("Harvest failed: {}", e.getMessage(), e);
        } finally {
            isRunning.set(false);
        }

        return getStatus();
    }

    private void resetCounters() {
        currentStatus = "STARTING";
        currentTask = "";
        totalSearches.set(0);
        completedSearches.set(0);
        foundCount.set(0);
        newCount.set(0);
        duplicateCount.set(0);
        filteredCount.set(0);
        emailsScraped.set(0);
        phonesScraped.set(0);
        seenPlaceIds.clear();
        seenNames.clear();
        lastError = null;
        completedAt = null;
    }

    private void runGooglePlacesHarvest() {
        currentStatus = "HARVESTING_GOOGLE_PLACES";

        // Pre-load existing place IDs
        supplierRepository.findAll().forEach(s -> {
            if (s.getGooglePlaceId() != null) seenPlaceIds.add(s.getGooglePlaceId());
            if (s.getCompanyName() != null) seenNames.add(normalizeCompanyName(s.getCompanyName()));
        });
        log.info("Loaded {} existing suppliers", seenPlaceIds.size());

        // 12 categories × 14 cities = 168 searches
        totalSearches.set(SEARCH_TERMS.size() * CITIES.length);
        log.info("Starting Google Places harvest: {} categories × {} cities = {} searches",
            SEARCH_TERMS.size(), CITIES.length, totalSearches.get());

        for (Map.Entry<String, String> entry : SEARCH_TERMS.entrySet()) {
            String category = entry.getKey();
            String searchTerm = entry.getValue();

            for (String city : CITIES) {
                try {
                    currentTask = category + " in " + city;
                    log.info("[{}/{}] Searching: {} in {}",
                        completedSearches.get() + 1, totalSearches.get(), searchTerm, city);

                    List<GooglePlacesService.PlaceResult> results =
                        googlePlacesService.searchPlaces(searchTerm, city);

                    foundCount.addAndGet(results.size());
                    log.info("  Found {} results", results.size());

                    for (GooglePlacesService.PlaceResult place : results) {
                        processPlace(place, category, city);
                    }

                    completedSearches.incrementAndGet();
                    Thread.sleep(1000); // Rate limit: 1/sec

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Harvest interrupted", e);
                } catch (Exception e) {
                    log.error("Error processing {} in {}: {}", category, city, e.getMessage());
                    lastError = category + "/" + city + ": " + e.getMessage();
                    completedSearches.incrementAndGet();
                }
            }
        }

        log.info("Google Places harvest complete: {} new suppliers", newCount.get());
    }

    private void processPlace(GooglePlacesService.PlaceResult place, String category, String searchCity) {
        // Skip duplicates by place ID
        if (place.placeId != null && seenPlaceIds.contains(place.placeId)) {
            duplicateCount.incrementAndGet();
            return;
        }

        // Skip duplicates by name
        String normalizedName = normalizeCompanyName(place.name);
        if (seenNames.contains(normalizedName)) {
            duplicateCount.incrementAndGet();
            return;
        }

        // Skip blacklisted names
        if (isBlacklisted(place.name)) {
            filteredCount.incrementAndGet();
            return;
        }

        // Check DB for existing
        if (place.placeId != null && supplierRepository.existsByGooglePlaceId(place.placeId)) {
            seenPlaceIds.add(place.placeId);
            duplicateCount.incrementAndGet();
            return;
        }

        // Create supplier
        Supplier supplier = new Supplier();
        supplier.setGooglePlaceId(place.placeId);
        supplier.setCompanyName(place.name);
        supplier.setAddress(place.formattedAddress);
        supplier.setPhone(place.phone);
        supplier.setWebsite(place.website);
        supplier.setCity(place.city != null ? place.city : searchCity);
        supplier.setGoogleRating(place.rating);
        supplier.setGoogleReviewCount(place.reviewCount);
        supplier.setSource("GOOGLE_PLACES");
        supplier.setCategories(new String[]{category});
        supplier.setServiceAreas(new String[]{supplier.getCity().toUpperCase()});
        supplier.setIsVerified(false);
        supplier.setCreatedAt(LocalDateTime.now());
        supplier.setUpdatedAt(LocalDateTime.now());
        supplier.setTrustScore(calculateTrustScore(place));

        supplierRepository.save(supplier);
        if (place.placeId != null) seenPlaceIds.add(place.placeId);
        seenNames.add(normalizedName);
        newCount.incrementAndGet();

        log.debug("  + Added: {} ({})", place.name, place.city);
    }

    private void filterNonConstruction() {
        currentStatus = "FILTERING";
        currentTask = "Removing non-construction businesses";
        log.info("Filtering non-construction businesses...");

        List<Supplier> toDelete = new ArrayList<>();

        for (Supplier s : supplierRepository.findAll()) {
            if (isBlacklisted(s.getCompanyName())) {
                toDelete.add(s);
            }
        }

        if (!toDelete.isEmpty()) {
            supplierRepository.deleteAll(toDelete);
            filteredCount.addAndGet(toDelete.size());
            log.info("Removed {} non-construction entries", toDelete.size());
        }
    }

    private void scrapeWebsitesForContacts() {
        currentStatus = "SCRAPING_WEBSITES";
        log.info("Scraping websites for contact info...");

        List<Supplier> suppliersWithWebsite = supplierRepository.findAll().stream()
            .filter(s -> s.getWebsite() != null && !s.getWebsite().isEmpty())
            .filter(s -> s.getEmail() == null || s.getEmail().isEmpty())
            .collect(Collectors.toList());

        log.info("Found {} suppliers with website but no email", suppliersWithWebsite.size());
        int count = 0;

        for (Supplier supplier : suppliersWithWebsite) {
            try {
                count++;
                currentTask = "Scraping " + count + "/" + suppliersWithWebsite.size() + ": " + supplier.getWebsite();

                String html = fetchWebsite(supplier.getWebsite());
                if (html != null) {
                    // Extract email
                    Set<String> emails = extractEmails(html);
                    if (!emails.isEmpty()) {
                        String bestEmail = chooseBestEmail(emails, supplier.getCompanyName());
                        if (bestEmail != null) {
                            supplier.setEmail(bestEmail);
                            emailsScraped.incrementAndGet();
                            log.debug("  Found email for {}: {}", supplier.getCompanyName(), bestEmail);
                        }
                    }

                    // Extract phone if missing
                    if (supplier.getPhone() == null || supplier.getPhone().isEmpty()) {
                        Set<String> phones = extractPhones(html);
                        if (!phones.isEmpty()) {
                            supplier.setPhone(phones.iterator().next());
                            phonesScraped.incrementAndGet();
                        }
                    }

                    supplier.setUpdatedAt(LocalDateTime.now());
                    supplierRepository.save(supplier);
                }

                Thread.sleep(1000); // Rate limit: 1 website/sec

            } catch (Exception e) {
                log.debug("Error scraping {}: {}", supplier.getWebsite(), e.getMessage());
            }
        }

        log.info("Website scraping complete: {} emails, {} phones found",
            emailsScraped.get(), phonesScraped.get());
    }

    private String fetchWebsite(String urlStr) {
        try {
            if (!urlStr.startsWith("http")) {
                urlStr = "https://" + urlStr;
            }

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; BuildQuote/1.0)");
            conn.setInstanceFollowRedirects(true);

            int status = conn.getResponseCode();
            if (status != 200) return null;

            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))) {
                String line;
                int lines = 0;
                while ((line = reader.readLine()) != null && lines < 500) {
                    content.append(line).append("\n");
                    lines++;
                }
            }

            return content.toString();

        } catch (Exception e) {
            return null;
        }
    }

    private Set<String> extractEmails(String html) {
        Set<String> emails = new HashSet<>();
        Matcher matcher = EMAIL_PATTERN.matcher(html.toLowerCase());
        while (matcher.find()) {
            String email = matcher.group();
            // Filter out common non-business emails
            if (!email.contains("example.com") &&
                !email.contains("domain.com") &&
                !email.contains("email.com") &&
                !email.contains("wixpress") &&
                !email.contains("sentry.io") &&
                !email.contains("google") &&
                !email.contains("facebook") &&
                !email.endsWith(".png") &&
                !email.endsWith(".jpg") &&
                !email.endsWith(".gif")) {
                emails.add(email);
            }
        }
        return emails;
    }

    private Set<String> extractPhones(String html) {
        Set<String> phones = new HashSet<>();
        Matcher matcher = PHONE_PATTERN.matcher(html);
        while (matcher.find()) {
            String phone = matcher.group().replaceAll("\\s+", "");
            if (phone.length() >= 7) {
                if (!phone.startsWith("+") && !phone.startsWith("372")) {
                    phone = "+372" + phone;
                } else if (phone.startsWith("372")) {
                    phone = "+" + phone;
                }
                phones.add(phone);
            }
        }
        return phones;
    }

    private String chooseBestEmail(Set<String> emails, String companyName) {
        // Prefer info@, kontakt@, office@ emails
        for (String email : emails) {
            if (email.startsWith("info@") || email.startsWith("kontakt@") ||
                email.startsWith("office@") || email.startsWith("post@")) {
                return email;
            }
        }
        // Otherwise return first valid email
        return emails.isEmpty() ? null : emails.iterator().next();
    }

    private void deduplicateSuppliers() {
        currentStatus = "DEDUPLICATING";
        currentTask = "Removing duplicates";
        log.info("Deduplicating suppliers...");

        Map<String, List<Supplier>> byName = supplierRepository.findAll().stream()
            .collect(Collectors.groupingBy(s -> normalizeCompanyName(s.getCompanyName())));

        int removed = 0;
        for (Map.Entry<String, List<Supplier>> entry : byName.entrySet()) {
            List<Supplier> dups = entry.getValue();
            if (dups.size() > 1) {
                // Keep the one with most contact info
                dups.sort((a, b) -> {
                    int scoreA = contactScore(a);
                    int scoreB = contactScore(b);
                    return scoreB - scoreA; // Descending
                });

                // Delete all except first
                for (int i = 1; i < dups.size(); i++) {
                    supplierRepository.delete(dups.get(i));
                    removed++;
                }
            }
        }

        // Also dedupe by registry code
        Map<String, List<Supplier>> byRegCode = supplierRepository.findAll().stream()
            .filter(s -> s.getRegistryCode() != null && !s.getRegistryCode().isEmpty())
            .collect(Collectors.groupingBy(Supplier::getRegistryCode));

        for (Map.Entry<String, List<Supplier>> entry : byRegCode.entrySet()) {
            List<Supplier> dups = entry.getValue();
            if (dups.size() > 1) {
                dups.sort((a, b) -> contactScore(b) - contactScore(a));
                for (int i = 1; i < dups.size(); i++) {
                    supplierRepository.delete(dups.get(i));
                    removed++;
                }
            }
        }

        log.info("Deduplication complete: removed {} duplicates", removed);
        duplicateCount.addAndGet(removed);
    }

    private int contactScore(Supplier s) {
        int score = 0;
        if (s.getEmail() != null && !s.getEmail().isEmpty()) score += 3;
        if (s.getPhone() != null && !s.getPhone().isEmpty()) score += 2;
        if (s.getWebsite() != null && !s.getWebsite().isEmpty()) score += 1;
        if (s.getAddress() != null && !s.getAddress().isEmpty()) score += 1;
        if (s.getGoogleRating() != null) score += 1;
        return score;
    }

    private String normalizeCompanyName(String name) {
        if (name == null) return "";
        return name.toLowerCase()
            .replaceAll("\\s+", " ")
            .replaceAll("[^a-zäöüõ0-9 ]", "")
            .replaceAll("\\b(oü|ou|as|osaühing|aktsiaselts|mtü|fie)\\b", "")
            .trim();
    }

    private boolean isBlacklisted(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        for (String blacklisted : BLACKLIST_NAMES) {
            if (lower.contains(blacklisted.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private int calculateTrustScore(GooglePlacesService.PlaceResult place) {
        int score = 50;
        if (place.rating != null) {
            score += (int)(place.rating.doubleValue() * 5);
        }
        if (place.reviewCount != null && place.reviewCount > 10) {
            score += Math.min(place.reviewCount / 5, 15);
        }
        return Math.min(score, 100);
    }

    private void logFinalStats() {
        Map<String, Object> stats = getStats();
        log.info("========================================");
        log.info("HARVEST COMPLETED");
        log.info("========================================");
        log.info("Total companies: {}", stats.get("totalCompanies"));
        log.info("Companies with email: {}", stats.get("withEmail"));
        log.info("Companies with phone: {}", stats.get("withPhone"));
        log.info("Companies with website: {}", stats.get("withWebsite"));
        log.info("Emails scraped this run: {}", emailsScraped.get());
        log.info("Phones scraped this run: {}", phonesScraped.get());
        log.info("By source: {}", stats.get("bySource"));
        log.info("By city: {}", stats.get("byCity"));
        log.info("========================================");
    }

    // Additional method for Google Places only harvest
    @Transactional
    public Map<String, Object> runGooglePlacesOnly() {
        if (isRunning.get()) {
            return Map.of("success", false, "error", "Harvest is already running");
        }
        if (!googlePlacesService.isConfigured()) {
            return Map.of("success", false, "error", "Google Places API key not configured");
        }

        resetCounters();
        isRunning.set(true);
        startedAt = LocalDateTime.now();

        try {
            runGooglePlacesHarvest();
            filterNonConstruction();
            currentStatus = "COMPLETED";
            completedAt = LocalDateTime.now();
            logFinalStats();
        } catch (Exception e) {
            currentStatus = "FAILED";
            lastError = e.getMessage();
            log.error("Harvest failed: {}", e.getMessage(), e);
        } finally {
            isRunning.set(false);
        }

        return getStatus();
    }

    /**
     * PARALLEL Google Places harvest - 6x faster than sequential.
     * Runs all category searches for a city in parallel.
     */
    @Transactional
    public Map<String, Object> runParallelHarvest() {
        if (isRunning.get()) {
            return Map.of("success", false, "error", "Harvest is already running");
        }
        if (!googlePlacesService.isConfigured()) {
            return Map.of("success", false, "error", "Google Places API key not configured");
        }

        resetCounters();
        isRunning.set(true);
        startedAt = LocalDateTime.now();
        currentStatus = "HARVESTING_PARALLEL";

        try {
            // Pre-load existing place IDs
            supplierRepository.findAll().forEach(s -> {
                if (s.getGooglePlaceId() != null) seenPlaceIds.add(s.getGooglePlaceId());
                if (s.getCompanyName() != null) seenNames.add(normalizeCompanyName(s.getCompanyName()));
            });

            totalSearches.set(SEARCH_TERMS.size() * CITIES.length);
            log.info("Starting PARALLEL harvest: {} categories × {} cities = {} searches",
                SEARCH_TERMS.size(), CITIES.length, totalSearches.get());

            // Process each city with all categories in parallel
            for (String city : CITIES) {
                currentTask = "Parallel search in " + city;
                log.info("Searching {} categories in {} in parallel...", SEARCH_TERMS.size(), city);

                List<CompletableFuture<List<GooglePlacesService.PlaceResult>>> futures = new ArrayList<>();

                for (Map.Entry<String, String> entry : SEARCH_TERMS.entrySet()) {
                    String category = entry.getKey();
                    String searchTerm = entry.getValue();

                    CompletableFuture<List<GooglePlacesService.PlaceResult>> future =
                        CompletableFuture.supplyAsync(() -> {
                            try {
                                return googlePlacesService.searchPlaces(searchTerm, city);
                            } catch (Exception e) {
                                log.warn("Search failed for {} in {}: {}", category, city, e.getMessage());
                                return Collections.emptyList();
                            }
                        }, parallelExecutor);

                    futures.add(future);
                }

                // Wait for all searches in this city to complete
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(60, TimeUnit.SECONDS);

                // Process results
                int categoryIndex = 0;
                for (Map.Entry<String, String> entry : SEARCH_TERMS.entrySet()) {
                    String category = entry.getKey();
                    try {
                        List<GooglePlacesService.PlaceResult> results = futures.get(categoryIndex).get();
                        foundCount.addAndGet(results.size());

                        for (GooglePlacesService.PlaceResult place : results) {
                            processPlace(place, category, city);
                        }

                        completedSearches.incrementAndGet();
                    } catch (Exception e) {
                        log.warn("Failed to process results for {} in {}", category, city);
                        completedSearches.incrementAndGet();
                    }
                    categoryIndex++;
                }

                log.info("  {} completed: {}/{} total searches done",
                    city, completedSearches.get(), totalSearches.get());

                // Brief pause between cities to avoid API limits
                Thread.sleep(500);
            }

            filterNonConstruction();
            currentStatus = "COMPLETED";
            completedAt = LocalDateTime.now();
            logFinalStats();

        } catch (Exception e) {
            currentStatus = "FAILED";
            lastError = e.getMessage();
            log.error("Parallel harvest failed: {}", e.getMessage(), e);
        } finally {
            isRunning.set(false);
        }

        return getStatus();
    }

    // Method to just scrape emails
    @Transactional
    public Map<String, Object> runEmailScrape() {
        if (isRunning.get()) {
            return Map.of("success", false, "error", "Already running");
        }

        isRunning.set(true);
        currentStatus = "SCRAPING_WEBSITES";
        startedAt = LocalDateTime.now();
        emailsScraped.set(0);
        phonesScraped.set(0);

        try {
            scrapeWebsitesForContacts();
            currentStatus = "COMPLETED";
            completedAt = LocalDateTime.now();
        } catch (Exception e) {
            currentStatus = "FAILED";
            lastError = e.getMessage();
        } finally {
            isRunning.set(false);
        }

        return getStatus();
    }

    // Method to deduplicate only
    @Transactional
    public Map<String, Object> runDeduplicate() {
        if (isRunning.get()) {
            return Map.of("success", false, "error", "Already running");
        }

        isRunning.set(true);
        currentStatus = "DEDUPLICATING";
        startedAt = LocalDateTime.now();
        duplicateCount.set(0);

        try {
            deduplicateSuppliers();
            currentStatus = "COMPLETED";
            completedAt = LocalDateTime.now();
        } catch (Exception e) {
            currentStatus = "FAILED";
            lastError = e.getMessage();
        } finally {
            isRunning.set(false);
        }

        return getStatus();
    }
}
