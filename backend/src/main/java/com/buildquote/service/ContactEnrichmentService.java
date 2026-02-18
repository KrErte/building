package com.buildquote.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Combined contact enrichment service.
 * Step 1: Google Places API -> phone + website
 * Step 2: Website scraping -> email (+ phone fallback)
 */
@Service
public class ContactEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(ContactEnrichmentService.class);

    private final GooglePlacesService googlePlacesService;
    private final JdbcTemplate jdbcTemplate;

    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "(?:\\+372\\s?)?(?:[5-9]\\d{2}[\\s-]?\\d{4}|\\d{3}[\\s-]?\\d{4})"
    );
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
    );

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger totalCount = new AtomicInteger(0);
    private final AtomicInteger googleFoundCount = new AtomicInteger(0);
    private final AtomicInteger websiteFoundCount = new AtomicInteger(0);
    private final AtomicInteger phoneFoundCount = new AtomicInteger(0);
    private final AtomicInteger emailFoundCount = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final AtomicInteger skippedCount = new AtomicInteger(0);
    private volatile String currentCompany = "";
    private volatile String currentStep = "IDLE";
    private volatile LocalDateTime startedAt = null;

    public ContactEnrichmentService(GooglePlacesService googlePlacesService, JdbcTemplate jdbcTemplate) {
        this.googlePlacesService = googlePlacesService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("isRunning", isRunning.get());
        status.put("currentStep", currentStep);
        status.put("currentCompany", currentCompany);
        status.put("processed", processedCount.get());
        status.put("total", totalCount.get());
        status.put("googleFound", googleFoundCount.get());
        status.put("websiteFound", websiteFoundCount.get());
        status.put("phoneFound", phoneFoundCount.get());
        status.put("emailFound", emailFoundCount.get());
        status.put("errors", errorCount.get());
        status.put("skipped", skippedCount.get());
        status.put("startedAt", startedAt);

        int total = totalCount.get();
        int processed = processedCount.get();
        status.put("progressPercent", total > 0 ? (processed * 100 / total) : 0);

        if (startedAt != null && processed > 0) {
            long elapsedMs = Duration.between(startedAt, LocalDateTime.now()).toMillis();
            long msPerCompany = elapsedMs / processed;
            long remainingMs = msPerCompany * (total - processed);
            status.put("estimatedMinutesRemaining", remainingMs / 60000);
        }

        return status;
    }

    public String startEnrichment(int limit) {
        if (!googlePlacesService.isConfigured()) {
            return "Google Places API key not configured";
        }
        if (isRunning.get()) {
            return "Enrichment is already running";
        }

        // Run in separate thread (self-invocation bypasses Spring @Async proxy)
        new Thread(() -> enrichAsync(limit), "contact-enrichment").start();
        return "Contact enrichment started for " + limit + " companies";
    }

    public void stopEnrichment() {
        isRunning.set(false);
    }

    public void enrichAsync(int limit) {
        isRunning.set(true);
        resetCounters();
        startedAt = LocalDateTime.now();

        try {
            String sql = """
                SELECT id, legal_name, city, county, website, phone, email
                FROM crawler.company
                WHERE (phone IS NULL OR phone = '{}' OR array_length(phone, 1) IS NULL)
                   OR (email IS NULL OR email = '{}' OR array_length(email, 1) IS NULL)
                ORDER BY legal_name
                LIMIT ?
                """;

            List<Map<String, Object>> companies = jdbcTemplate.queryForList(sql, limit);
            totalCount.set(companies.size());

            log.info("Starting contact enrichment for {} companies", companies.size());

            for (Map<String, Object> company : companies) {
                if (!isRunning.get()) {
                    log.info("Enrichment stopped by user");
                    currentStep = "STOPPED";
                    break;
                }

                processCompany(company);
                processedCount.incrementAndGet();

                if (processedCount.get() % 50 == 0) {
                    log.info("Enrichment progress: {}/{} | phone:{} email:{} errors:{}",
                        processedCount.get(), totalCount.get(),
                        phoneFoundCount.get(), emailFoundCount.get(),
                        errorCount.get());
                }
            }
        } catch (Exception e) {
            log.error("Enrichment error: {}", e.getMessage(), e);
        } finally {
            isRunning.set(false);
            currentStep = "COMPLETED";
            currentCompany = "";
            log.info("Enrichment finished. Processed:{}, Phone:{}, Email:{}, Errors:{}",
                processedCount.get(), phoneFoundCount.get(),
                emailFoundCount.get(), errorCount.get());
        }
    }

    private void processCompany(Map<String, Object> company) {
        String id = company.get("id").toString();
        String name = (String) company.get("legal_name");
        String city = (String) company.get("city");
        String website = (String) company.get("website");

        boolean hasPhone = hasArrayData(company.get("phone"));
        boolean hasEmail = hasArrayData(company.get("email"));
        boolean hasWebsite = website != null && !website.isEmpty();

        if (hasPhone && hasEmail) {
            skippedCount.incrementAndGet();
            return;
        }

        if (name == null || name.isEmpty()) {
            skippedCount.incrementAndGet();
            return;
        }

        currentCompany = name;

        String foundPhone = null;
        String foundEmail = null;
        String foundWebsite = website;

        // ===== STEP 1: Google Places =====
        if (!hasPhone || !hasWebsite) {
            currentStep = "GOOGLE_PLACES";
            try {
                List<GooglePlacesService.PlaceResult> results =
                    googlePlacesService.searchPlaces(name, city != null ? city : "Estonia");

                if (!results.isEmpty()) {
                    GooglePlacesService.PlaceResult best = results.get(0);

                    if (!hasPhone && best.phone != null && !best.phone.isEmpty()) {
                        foundPhone = best.phone;
                        googleFoundCount.incrementAndGet();
                    }

                    if (!hasWebsite && best.website != null && !best.website.isEmpty()) {
                        foundWebsite = best.website;
                    }
                }

                Thread.sleep(200);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.debug("Google Places error for {}: {}", name, e.getMessage());
                errorCount.incrementAndGet();
            }
        }

        // ===== STEP 2: Website scraping =====
        boolean needsPhone = !hasPhone && foundPhone == null;
        boolean needsEmail = !hasEmail;

        if ((needsPhone || needsEmail) && foundWebsite != null && !foundWebsite.isEmpty()) {
            currentStep = "WEBSITE_SCRAPE";
            try {
                ContactInfo scraped = crawlWebsite(foundWebsite);

                if (needsPhone && scraped.phone != null) {
                    foundPhone = scraped.phone;
                    websiteFoundCount.incrementAndGet();
                }

                if (needsEmail && scraped.email != null) {
                    foundEmail = scraped.email;
                }

                Thread.sleep(500);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.debug("Website scrape error for {} ({}): {}", name, foundWebsite, e.getMessage());
                errorCount.incrementAndGet();
            }
        }

        // ===== STEP 3: Update database =====
        currentStep = "UPDATING";
        updateCompanyContact(id, foundPhone, foundEmail, foundWebsite, hasPhone, hasWebsite);

        if (foundPhone != null) phoneFoundCount.incrementAndGet();
        if (foundEmail != null) emailFoundCount.incrementAndGet();

        if (foundPhone != null || foundEmail != null) {
            log.info("Enriched {}: phone={}, email={}, website={}",
                name, foundPhone, foundEmail, foundWebsite);
        }
    }

    private boolean hasArrayData(Object arrayValue) {
        if (arrayValue == null) return false;
        String str = arrayValue.toString();
        return !str.isEmpty() && !str.equals("{}") && !str.equals("NULL");
    }

    // ===== Website crawling (from WebsiteCrawlerService) =====

    private ContactInfo crawlWebsite(String websiteUrl) {
        ContactInfo info = new ContactInfo();

        try {
            if (!websiteUrl.startsWith("http")) {
                websiteUrl = "https://" + websiteUrl;
            }

            Document doc = Jsoup.connect(websiteUrl)
                .userAgent("Mozilla/5.0 (compatible; BuildQuote/1.0)")
                .timeout(10000)
                .followRedirects(true)
                .get();

            extractContactInfo(doc, info);

            if (!info.hasInfo()) {
                String contactUrl = findContactPageUrl(doc, websiteUrl);
                if (contactUrl != null) {
                    try {
                        Document contactDoc = Jsoup.connect(contactUrl)
                            .userAgent("Mozilla/5.0 (compatible; BuildQuote/1.0)")
                            .timeout(10000)
                            .followRedirects(true)
                            .get();
                        extractContactInfo(contactDoc, info);
                    } catch (Exception e) {
                        // Ignore contact page errors
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to crawl: " + e.getMessage());
        }

        return info;
    }

    private void extractContactInfo(Document doc, ContactInfo info) {
        String text = doc.text();

        // Extract phone from tel: links first (most reliable)
        Elements telLinks = doc.select("a[href^=tel:]");
        for (Element link : telLinks) {
            String phone = link.attr("href").replace("tel:", "").trim();
            phone = cleanPhone(phone);
            if (isValidEstonianPhone(phone) && info.phone == null) {
                info.phone = phone;
                break;
            }
        }

        // Extract email from mailto: links
        Elements mailLinks = doc.select("a[href^=mailto:]");
        for (Element link : mailLinks) {
            String email = link.attr("href").replace("mailto:", "").trim();
            if (email.contains("?")) {
                email = email.substring(0, email.indexOf("?"));
            }
            if (isValidEmail(email) && info.email == null) {
                info.email = email.toLowerCase();
                break;
            }
        }

        // Regex fallback for phone
        if (info.phone == null) {
            Matcher phoneMatcher = PHONE_PATTERN.matcher(text);
            Set<String> phones = new HashSet<>();
            while (phoneMatcher.find()) {
                String phone = cleanPhone(phoneMatcher.group());
                if (isValidEstonianPhone(phone)) {
                    phones.add(phone);
                }
            }
            if (!phones.isEmpty()) {
                info.phone = phones.iterator().next();
            }
        }

        // Regex fallback for email
        if (info.email == null) {
            Matcher emailMatcher = EMAIL_PATTERN.matcher(text);
            while (emailMatcher.find()) {
                String email = emailMatcher.group().toLowerCase();
                if (!email.contains("example.com") &&
                    !email.contains("domain.com") &&
                    !email.startsWith("info@w3") &&
                    !email.contains("@sentry")) {
                    info.email = email;
                    break;
                }
            }
        }
    }

    private String findContactPageUrl(Document doc, String baseUrl) {
        String[] patterns = {"kontakt", "contact", "meist", "about", "info"};

        Elements links = doc.select("a[href]");
        for (Element link : links) {
            String href = link.attr("abs:href").toLowerCase();
            String text = link.text().toLowerCase();

            for (String pattern : patterns) {
                if (href.contains(pattern) || text.contains(pattern)) {
                    return link.attr("abs:href");
                }
            }
        }

        return null;
    }

    private String cleanPhone(String phone) {
        String cleaned = phone.replaceAll("[^0-9+]", "");
        if (cleaned.startsWith("+372")) {
            cleaned = cleaned.substring(4);
        }
        if (cleaned.startsWith("372") && cleaned.length() > 10) {
            cleaned = cleaned.substring(3);
        }
        return cleaned;
    }

    private boolean isValidEstonianPhone(String phone) {
        if (phone == null || phone.isEmpty()) return false;
        return phone.matches("\\d{7,8}") && phone.charAt(0) >= '2';
    }

    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) return false;
        return EMAIL_PATTERN.matcher(email).matches() &&
               email.contains("@") &&
               email.contains(".");
    }

    // ===== Database update =====

    private void updateCompanyContact(String id, String phone, String email,
                                       String website, boolean hadPhone, boolean hadWebsite) {
        List<Object> params = new ArrayList<>();
        List<String> updates = new ArrayList<>();

        if (phone != null && !phone.isEmpty() && !hadPhone) {
            updates.add("phone = ARRAY[?]");
            params.add(phone);
        }
        if (email != null && !email.isEmpty()) {
            updates.add("email = ARRAY[?]");
            params.add(email);
        }
        if (website != null && !website.isEmpty() && !hadWebsite) {
            updates.add("website = ?");
            params.add(website);
        }

        if (updates.isEmpty()) return;

        String sql = "UPDATE crawler.company SET " +
            String.join(", ", updates) +
            " WHERE id = ?::uuid";
        params.add(id);

        jdbcTemplate.update(sql, params.toArray());
    }

    private void resetCounters() {
        processedCount.set(0);
        totalCount.set(0);
        googleFoundCount.set(0);
        websiteFoundCount.set(0);
        phoneFoundCount.set(0);
        emailFoundCount.set(0);
        errorCount.set(0);
        skippedCount.set(0);
        currentCompany = "";
        currentStep = "STARTING";
    }

    private static class ContactInfo {
        String phone;
        String email;

        boolean hasInfo() {
            return (phone != null && !phone.isEmpty()) ||
                   (email != null && !email.isEmpty());
        }
    }
}
