package com.buildquote.controller;

import com.buildquote.service.ContactCrawlerService;
import com.buildquote.service.WebsiteCrawlerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/crawler")
@CrossOrigin
public class CrawlerController {

    private final ContactCrawlerService contactCrawlerService;
    private final WebsiteCrawlerService websiteCrawlerService;

    public CrawlerController(ContactCrawlerService contactCrawlerService,
                            WebsiteCrawlerService websiteCrawlerService) {
        this.contactCrawlerService = contactCrawlerService;
        this.websiteCrawlerService = websiteCrawlerService;
    }

    // Google Places crawler (costs money)
    @GetMapping("/google/status")
    public ResponseEntity<Map<String, Object>> getGoogleStatus() {
        return ResponseEntity.ok(contactCrawlerService.getStatus());
    }

    @PostMapping("/google/start")
    public ResponseEntity<Map<String, String>> startGoogleCrawler(
            @RequestParam(defaultValue = "100") int limit) {
        String result = contactCrawlerService.startCrawling(limit);
        return ResponseEntity.ok(Map.of("message", result));
    }

    @PostMapping("/google/stop")
    public ResponseEntity<Map<String, String>> stopGoogleCrawler() {
        contactCrawlerService.stopCrawling();
        return ResponseEntity.ok(Map.of("message", "Google crawler stop requested"));
    }

    // Website crawler (free)
    @GetMapping("/website/status")
    public ResponseEntity<Map<String, Object>> getWebsiteStatus() {
        return ResponseEntity.ok(websiteCrawlerService.getStatus());
    }

    @PostMapping("/website/start")
    public ResponseEntity<Map<String, String>> startWebsiteCrawler(
            @RequestParam(defaultValue = "100") int limit) {
        String result = websiteCrawlerService.startCrawling(limit);
        return ResponseEntity.ok(Map.of("message", result));
    }

    @PostMapping("/website/stop")
    public ResponseEntity<Map<String, String>> stopWebsiteCrawler() {
        websiteCrawlerService.stopCrawling();
        return ResponseEntity.ok(Map.of("message", "Website crawler stop requested"));
    }

    // Combined status
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getCombinedStatus() {
        return ResponseEntity.ok(Map.of(
            "google", contactCrawlerService.getStatus(),
            "website", websiteCrawlerService.getStatus()
        ));
    }
}
