package com.buildquote.service;

import com.buildquote.dto.OnboardingDTO;
import com.buildquote.dto.OnboardingResponseDTO;
import com.buildquote.entity.Supplier;
import com.buildquote.repository.SupplierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class OnboardingService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingService.class);

    private final SupplierRepository supplierRepository;
    private final EmailService emailService;

    @Value("${app.base-url:http://localhost}")
    private String baseUrl;

    public OnboardingService(SupplierRepository supplierRepository, EmailService emailService) {
        this.supplierRepository = supplierRepository;
        this.emailService = emailService;
    }

    public Optional<OnboardingResponseDTO> getSupplierByToken(String token) {
        return supplierRepository.findByOnboardingToken(token)
            .map(supplier -> {
                OnboardingResponseDTO dto = new OnboardingResponseDTO();
                dto.setCompanyName(supplier.getCompanyName());
                dto.setCurrentEmail(supplier.getEmail());
                dto.setCurrentPhone(supplier.getPhone());
                dto.setCurrentCategories(supplier.getCategories() != null ?
                    Arrays.asList(supplier.getCategories()) : Collections.emptyList());
                dto.setCurrentServiceAreas(supplier.getServiceAreas() != null ?
                    Arrays.asList(supplier.getServiceAreas()) : Collections.emptyList());
                dto.setAlreadyOnboarded(supplier.getOnboardedAt() != null);
                return dto;
            });
    }

    @Transactional
    public boolean submitOnboarding(String token, OnboardingDTO dto) {
        Optional<Supplier> optSupplier = supplierRepository.findByOnboardingToken(token);
        if (optSupplier.isEmpty()) {
            return false;
        }

        Supplier supplier = optSupplier.get();

        // Update supplier with onboarding data
        if (dto.getCompanyName() != null && !dto.getCompanyName().isEmpty()) {
            supplier.setCompanyName(dto.getCompanyName());
        }
        if (dto.getContactPerson() != null && !dto.getContactPerson().isEmpty()) {
            supplier.setContactPerson(dto.getContactPerson());
        }
        if (dto.getEmail() != null && !dto.getEmail().isEmpty()) {
            supplier.setEmail(dto.getEmail());
        }
        if (dto.getPhone() != null && !dto.getPhone().isEmpty()) {
            supplier.setPhone(dto.getPhone());
        }
        if (dto.getCategories() != null && !dto.getCategories().isEmpty()) {
            supplier.setCategories(dto.getCategories().toArray(new String[0]));
        }
        if (dto.getServiceAreas() != null && !dto.getServiceAreas().isEmpty()) {
            supplier.setServiceAreas(dto.getServiceAreas().toArray(new String[0]));
        }
        if (dto.getAdditionalInfo() != null) {
            supplier.setAdditionalInfo(dto.getAdditionalInfo());
        }

        supplier.setOnboardedAt(LocalDateTime.now());
        supplier.setIsVerified(true);
        supplier.setUpdatedAt(LocalDateTime.now());

        supplierRepository.save(supplier);
        log.info("Supplier onboarded: {} ({})", supplier.getCompanyName(), supplier.getEmail());

        return true;
    }

    @Transactional
    public Map<String, Object> generateTokensForAll() {
        List<Supplier> suppliers = supplierRepository.findAll();
        int generated = 0;

        for (Supplier supplier : suppliers) {
            if (supplier.getOnboardingToken() == null) {
                supplier.setOnboardingToken(generateToken());
                supplier.setUpdatedAt(LocalDateTime.now());
                supplierRepository.save(supplier);
                generated++;
            }
        }

        log.info("Generated {} onboarding tokens", generated);
        return Map.of(
            "total", suppliers.size(),
            "generated", generated,
            "alreadyHadToken", suppliers.size() - generated
        );
    }

    @Transactional
    public Map<String, Object> sendOnboardingEmails(int limit, boolean dryRun) {
        // Find suppliers with email and token, but not yet sent onboarding email
        List<Supplier> suppliers = supplierRepository.findAll().stream()
            .filter(s -> s.getEmail() != null && !s.getEmail().isEmpty())
            .filter(s -> s.getOnboardingToken() != null)
            .filter(s -> s.getOnboardingEmailSentAt() == null)
            .filter(s -> s.getOnboardedAt() == null)
            .limit(limit)
            .toList();

        log.info("Found {} suppliers eligible for onboarding email (limit: {}, dryRun: {})",
            suppliers.size(), limit, dryRun);

        AtomicInteger sent = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        List<String> sentTo = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (Supplier supplier : suppliers) {
            try {
                String onboardUrl = baseUrl + "/onboard/" + supplier.getOnboardingToken();

                if (!dryRun) {
                    emailService.sendOnboardingEmail(
                        supplier.getEmail(),
                        supplier.getCompanyName(),
                        onboardUrl
                    );

                    supplier.setOnboardingEmailSentAt(LocalDateTime.now());
                    supplier.setUpdatedAt(LocalDateTime.now());
                    supplierRepository.save(supplier);
                }

                sent.incrementAndGet();
                sentTo.add(supplier.getCompanyName() + " <" + supplier.getEmail() + ">");

                // Rate limit: 1 email per second
                if (!dryRun) {
                    Thread.sleep(1000);
                }

            } catch (Exception e) {
                failed.incrementAndGet();
                errors.add(supplier.getEmail() + ": " + e.getMessage());
                log.error("Failed to send onboarding email to {}: {}", supplier.getEmail(), e.getMessage());
            }
        }

        return Map.of(
            "dryRun", dryRun,
            "eligible", suppliers.size(),
            "sent", sent.get(),
            "failed", failed.get(),
            "sentTo", sentTo,
            "errors", errors
        );
    }

    public Map<String, Object> getOnboardingStats() {
        List<Supplier> all = supplierRepository.findAll();

        long withToken = all.stream().filter(s -> s.getOnboardingToken() != null).count();
        long emailSent = all.stream().filter(s -> s.getOnboardingEmailSentAt() != null).count();
        long onboarded = all.stream().filter(s -> s.getOnboardedAt() != null).count();
        long withEmail = all.stream().filter(s -> s.getEmail() != null && !s.getEmail().isEmpty()).count();
        long eligibleToSend = all.stream()
            .filter(s -> s.getEmail() != null && !s.getEmail().isEmpty())
            .filter(s -> s.getOnboardingToken() != null)
            .filter(s -> s.getOnboardingEmailSentAt() == null)
            .filter(s -> s.getOnboardedAt() == null)
            .count();

        return Map.of(
            "total", all.size(),
            "withEmail", withEmail,
            "withToken", withToken,
            "emailsSent", emailSent,
            "onboarded", onboarded,
            "eligibleToSend", eligibleToSend,
            "conversionRate", emailSent > 0 ? String.format("%.1f%%", (onboarded * 100.0 / emailSent)) : "0%"
        );
    }

    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    /**
     * Send a test onboarding email to a specific address
     */
    public Map<String, Object> sendTestEmail(String testEmail) {
        // Find any supplier with a token to use as example
        Optional<Supplier> sampleSupplier = supplierRepository.findAll().stream()
            .filter(s -> s.getOnboardingToken() != null)
            .findFirst();

        if (sampleSupplier.isEmpty()) {
            return Map.of(
                "success", false,
                "error", "No supplier with token found"
            );
        }

        Supplier supplier = sampleSupplier.get();
        String onboardUrl = baseUrl + "/onboard/" + supplier.getOnboardingToken();

        try {
            emailService.sendOnboardingEmail(
                testEmail,
                supplier.getCompanyName() + " (TEST)",
                onboardUrl
            );

            log.info("Test email sent to: {}", testEmail);
            return Map.of(
                "success", true,
                "sentTo", testEmail,
                "sampleCompany", supplier.getCompanyName(),
                "message", "Test email sent successfully"
            );
        } catch (Exception e) {
            log.error("Failed to send test email: {}", e.getMessage());
            return Map.of(
                "success", false,
                "error", e.getMessage()
            );
        }
    }
}
