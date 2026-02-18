package com.buildquote.controller;

import com.buildquote.dto.OnboardingDTO;
import com.buildquote.dto.OnboardingResponseDTO;
import com.buildquote.service.OnboardingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/onboard")
public class OnboardingController {

    private static final Logger log = LoggerFactory.getLogger(OnboardingController.class);

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    /**
     * Get supplier info by onboarding token (public endpoint)
     */
    @GetMapping("/{token}")
    public ResponseEntity<?> getSupplierByToken(@PathVariable String token) {
        return onboardingService.getSupplierByToken(token)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Submit onboarding form (public endpoint)
     */
    @PostMapping("/{token}")
    public ResponseEntity<?> submitOnboarding(
            @PathVariable String token,
            @RequestBody OnboardingDTO dto) {
        log.info("Onboarding submission for token: {}", token);

        boolean success = onboardingService.submitOnboarding(token, dto);
        if (success) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Aitäh! Teie ettevõte on registreeritud."
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Token ei ole kehtiv või on aegunud."
            ));
        }
    }

    /**
     * Generate onboarding tokens for all suppliers (admin endpoint)
     */
    @Secured("ROLE_ADMIN")
    @PostMapping("/admin/generate-tokens")
    public ResponseEntity<Map<String, Object>> generateTokens() {
        log.info("Generating onboarding tokens for all suppliers...");
        return ResponseEntity.ok(onboardingService.generateTokensForAll());
    }

    /**
     * Send onboarding emails (admin endpoint)
     */
    @Secured("ROLE_ADMIN")
    @PostMapping("/admin/send-emails")
    public ResponseEntity<Map<String, Object>> sendEmails(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        log.info("Sending onboarding emails (limit: {}, dryRun: {})", limit, dryRun);
        return ResponseEntity.ok(onboardingService.sendOnboardingEmails(limit, dryRun));
    }

    /**
     * Get onboarding campaign statistics (admin endpoint)
     */
    @Secured("ROLE_ADMIN")
    @GetMapping("/admin/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(onboardingService.getOnboardingStats());
    }

    /**
     * Mass send onboarding emails with dryRun support (admin endpoint)
     */
    @Secured("ROLE_ADMIN")
    @PostMapping("/mass-send")
    public ResponseEntity<Map<String, Object>> massSend(@RequestBody Map<String, Object> request) {
        boolean dryRun = request.get("dryRun") != null && (Boolean) request.get("dryRun");
        String testEmail = (String) request.get("testEmail");

        log.info("Mass send request - dryRun: {}, testEmail: {}", dryRun, testEmail);

        if (testEmail != null && !testEmail.isEmpty()) {
            return ResponseEntity.ok(onboardingService.sendTestEmail(testEmail));
        } else {
            return ResponseEntity.ok(onboardingService.sendOnboardingEmails(10, dryRun));
        }
    }
}
