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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WebsiteCrawlerService {

    private static final Logger log = LoggerFactory.getLogger(WebsiteCrawlerService.class);

    private final JdbcTemplate jdbcTemplate;

    // Estonian phone patterns: +372 XXXX XXXX, 5XX XXXX, 6XX XXXX, etc.
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "(?:\\+372\\s?)?(?:[5-9]\\d{2}[\\s-]?\\d{4}|\\d{3}[\\s-]?\\d{4})"
    );

    // Email pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
    );

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger foundCount = new AtomicInteger(0);
    private final AtomicInteger totalCount = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);

    public WebsiteCrawlerService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getStatus() {
        return Map.of(
            "isRunning", isRunning.get(),
            "processed", processedCount.get(),
            "found", foundCount.get(),
            "total", totalCount.get(),
            "errors", errorCount.get()
        );
    }

    public String startCrawling(int limit) {
        if (isRunning.get()) {
            return "Website crawler is already running";
        }

        crawlAsync(limit);
        return "Website crawler started for " + limit + " companies";
    }

    public void stopCrawling() {
        isRunning.set(false);
    }

    @Async
    public void crawlAsync(int limit) {
        isRunning.set(true);
        processedCount.set(0);
        foundCount.set(0);
        errorCount.set(0);

        try {
            // Get companies with website but without phone
            String sql = """
                SELECT id, legal_name, website
                FROM crawler.company
                WHERE website IS NOT NULL
                  AND website <> ''
                  AND (phone IS NULL OR phone = '{}' OR array_length(phone, 1) IS NULL)
                  AND (email IS NULL OR email = '{}' OR array_length(email, 1) IS NULL)
                LIMIT ?
                """;

            List<Map<String, Object>> companies = jdbcTemplate.queryForList(sql, limit);
            totalCount.set(companies.size());

            log.info("Starting website crawl for {} companies", companies.size());

            for (Map<String, Object> company : companies) {
                if (!isRunning.get()) {
                    log.info("Website crawler stopped by user");
                    break;
                }

                String id = company.get("id").toString();
                String name = (String) company.get("legal_name");
                String website = (String) company.get("website");

                if (website == null || website.isEmpty()) {
                    processedCount.incrementAndGet();
                    continue;
                }

                try {
                    ContactInfo contact = crawlWebsite(website);

                    if (contact.hasInfo()) {
                        updateCompanyContact(id, contact.phone, contact.email);
                        foundCount.incrementAndGet();
                        log.info("Found contact for {}: phone={}, email={}",
                            name, contact.phone, contact.email);
                    }

                    // Rate limiting - be nice to websites
                    Thread.sleep(500);

                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    log.debug("Error crawling {} ({}): {}", name, website, e.getMessage());
                }

                processedCount.incrementAndGet();

                if (processedCount.get() % 50 == 0) {
                    log.info("Website crawl progress: {}/{}, found: {}, errors: {}",
                        processedCount.get(), totalCount.get(), foundCount.get(), errorCount.get());
                }
            }

        } catch (Exception e) {
            log.error("Website crawler error: {}", e.getMessage(), e);
        } finally {
            isRunning.set(false);
            log.info("Website crawling finished. Processed: {}, Found: {}, Errors: {}",
                processedCount.get(), foundCount.get(), errorCount.get());
        }
    }

    private ContactInfo crawlWebsite(String websiteUrl) {
        ContactInfo info = new ContactInfo();

        try {
            // Normalize URL
            if (!websiteUrl.startsWith("http")) {
                websiteUrl = "https://" + websiteUrl;
            }

            // Fetch main page
            Document doc = Jsoup.connect(websiteUrl)
                .userAgent("Mozilla/5.0 (compatible; BuildQuote/1.0)")
                .timeout(10000)
                .followRedirects(true)
                .get();

            // Extract from main page
            extractContactInfo(doc, info);

            // If not found, try contact page
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
        String html = doc.html();

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
            // Remove any query params
            if (email.contains("?")) {
                email = email.substring(0, email.indexOf("?"));
            }
            if (isValidEmail(email) && info.email == null) {
                info.email = email.toLowerCase();
                break;
            }
        }

        // If still no phone, try regex on text
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

        // If still no email, try regex on text
        if (info.email == null) {
            Matcher emailMatcher = EMAIL_PATTERN.matcher(text);
            while (emailMatcher.find()) {
                String email = emailMatcher.group().toLowerCase();
                // Skip common non-contact emails
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
        // Common contact page patterns
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
        // Remove all non-digit characters except + at start
        String cleaned = phone.replaceAll("[^0-9+]", "");
        // Remove +372 prefix for consistency
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
        // Estonian numbers: 7-8 digits, starting with 5,6,7,8,9 (mobile) or 2-9 (landline)
        return phone.matches("\\d{7,8}") && phone.charAt(0) >= '2';
    }

    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) return false;
        return EMAIL_PATTERN.matcher(email).matches() &&
               email.contains("@") &&
               email.contains(".");
    }

    private void updateCompanyContact(String id, String phone, String email) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("UPDATE crawler.company SET ");
        List<String> updates = new ArrayList<>();

        if (phone != null && !phone.isEmpty()) {
            updates.add("phone = ARRAY[?]");
            params.add(phone);
        }
        if (email != null && !email.isEmpty()) {
            updates.add("email = ARRAY[?]");
            params.add(email);
        }

        if (updates.isEmpty()) return;

        sql.append(String.join(", ", updates));
        sql.append(" WHERE id = ?::uuid");
        params.add(id);

        jdbcTemplate.update(sql.toString(), params.toArray());
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
