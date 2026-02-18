package com.buildquote.service;

import com.buildquote.entity.CompanyEnrichment;
import com.buildquote.entity.Supplier;
import com.buildquote.repository.BidRepository;
import com.buildquote.repository.CompanyEnrichmentRepository;
import com.buildquote.repository.SupplierRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class CompanyEnrichmentPipelineService {

    private final CompanyEnrichmentRepository enrichmentRepository;
    private final SupplierRepository supplierRepository;
    private final BidRepository bidRepository;
    private final AnthropicService anthropicService;
    private final AiCacheService aiCacheService;
    private final EstonianRegistryService estonianRegistryService;
    private final ObjectMapper objectMapper;

    private static final int CACHE_DAYS = 30;

    @Transactional
    public CompanyEnrichment enrichTier1(UUID supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new RuntimeException("Supplier not found"));

        CompanyEnrichment enrichment = enrichmentRepository.findBySupplierId(supplierId)
                .orElse(CompanyEnrichment.builder().supplier(supplier).build());

        // Aggregate data from Google Places, web scraping, and existing supplier info
        Map<String, Object> facts = new HashMap<>();
        facts.put("companyName", supplier.getCompanyName());
        facts.put("email", supplier.getEmail());
        facts.put("phone", supplier.getPhone());
        facts.put("website", supplier.getWebsite());
        facts.put("address", supplier.getAddress());
        facts.put("city", supplier.getCity());
        facts.put("categories", supplier.getCategories());
        facts.put("googleRating", supplier.getGoogleRating());
        facts.put("googleReviewCount", supplier.getGoogleReviewCount());
        facts.put("registryCode", supplier.getRegistryCode());
        facts.put("isVerified", supplier.getIsVerified());
        facts.put("totalRfqsSent", supplier.getTotalRfqsSent());
        facts.put("totalBidsReceived", supplier.getTotalBidsReceived());
        facts.put("avgResponseTimeHours", supplier.getAvgResponseTimeHours());

        try {
            enrichment.setCrawlerFactsJson(objectMapper.writeValueAsString(facts));
        } catch (Exception e) {
            enrichment.setCrawlerFactsJson("{}");
        }

        // Query Estonian registries if registry code exists
        if (supplier.getRegistryCode() != null && !supplier.getRegistryCode().isBlank()) {
            try {
                var registryData = estonianRegistryService.queryRegistries(supplier.getRegistryCode());
                enrichment.setTaxDebt(registryData.getTaxDebt());
                enrichment.setTaxDebtAmount(registryData.getTaxDebtAmount());
                enrichment.setYearsInBusiness(registryData.getYearsInBusiness());
                enrichment.setAnnualRevenue(registryData.getAnnualRevenue());
                enrichment.setEmployeeCount(registryData.getEmployeeCount());
                enrichment.setPublicProcurementCount(registryData.getPublicProcurementCount());
                enrichment.setFinancialTrend(registryData.getFinancialTrend());
                enrichment.setRegistryDataJson(registryData.getRawJson());
                enrichment.setRegistryCheckedAt(LocalDateTime.now());
                log.info("Estonian registry data fetched for {}", supplier.getCompanyName());
            } catch (Exception e) {
                log.warn("Estonian registry query failed for {}: {}", supplier.getRegistryCode(), e.getMessage());
                // Graceful degradation - continue without registry data
            }
        }

        enrichment.setTier1CompletedAt(LocalDateTime.now());

        enrichment = enrichmentRepository.save(enrichment);
        log.info("Tier 1 enrichment completed for supplier: {}", supplier.getCompanyName());
        return enrichment;
    }

    @Transactional
    public CompanyEnrichment enrichTier2(UUID supplierId) {
        CompanyEnrichment enrichment = enrichmentRepository.findBySupplierId(supplierId)
                .orElseThrow(() -> new RuntimeException("Enrichment not found - run Tier 1 first"));

        if (enrichment.getCrawlerFactsJson() == null) {
            enrichTier1(supplierId);
            enrichment = enrichmentRepository.findBySupplierId(supplierId).orElseThrow();
        }

        String prompt = String.format("""
            Based on these facts about a construction company, generate:
            1. A professional summary (2-3 sentences)
            2. Their specialties/strengths as a comma-separated list

            Company facts: %s

            Return JSON:
            {
              "summary": "string",
              "specialties": "string - comma separated"
            }
            """, enrichment.getCrawlerFactsJson());

        Optional<String> cached = aiCacheService.getCached(prompt, "tier2-enrichment");
        String response = cached.orElseGet(() -> {
            String r = anthropicService.callClaude(prompt);
            if (r != null) aiCacheService.cache(prompt, "tier2-enrichment", r, 168); // 7 days
            return r;
        });

        if (response != null) {
            try {
                String json = extractJson(response);
                JsonNode root = objectMapper.readTree(json);
                enrichment.setLlmSummary(root.path("summary").asText());
                enrichment.setLlmSpecialties(root.path("specialties").asText());
            } catch (Exception e) {
                log.error("Error parsing Tier 2 response: {}", e.getMessage());
                enrichment.setLlmSummary(response);
            }
        }

        enrichment.setTier2CompletedAt(LocalDateTime.now());
        enrichment = enrichmentRepository.save(enrichment);
        log.info("Tier 2 enrichment completed for supplier: {}", enrichment.getSupplier().getCompanyName());
        return enrichment;
    }

    @Transactional
    public CompanyEnrichment enrichTier3(UUID supplierId) {
        CompanyEnrichment enrichment = enrichmentRepository.findBySupplierId(supplierId)
                .orElseThrow(() -> new RuntimeException("Enrichment not found - run Tier 1+2 first"));

        Supplier supplier = enrichment.getSupplier();

        // Build deep analysis prompt with bid history and registry data
        String taxDebtInfo = "Unknown";
        if (enrichment.getTaxDebt() != null) {
            taxDebtInfo = enrichment.getTaxDebt()
                    ? "YES - Amount: EUR " + (enrichment.getTaxDebtAmount() != null ? enrichment.getTaxDebtAmount() : "unknown")
                    : "No tax debt";
        }

        String prompt = String.format("""
            Deep analysis of construction company for risk/reliability scoring:

            Company: %s
            Facts: %s
            LLM Summary: %s
            Google Rating: %s (from %s reviews)
            Total RFQs Sent: %s
            Total Bids Received: %s
            Avg Response Time: %s hours
            Is Verified: %s

            Estonian Registry Data:
            - Tax Debt: %s (CRITICAL RISK FACTOR - companies with tax debt have higher default risk)
            - Years in Business: %s
            - Employee Count: %s
            - Annual Revenue: %s
            - Public Procurement Wins: %s

            Analyze and return JSON:
            {
              "riskScore": 0-100 (0=safest, 100=highest risk),
              "reliabilityScore": 0-100 (100=most reliable),
              "priceCompetitiveness": "LOW/MEDIUM/HIGH",
              "recommendedFor": "comma-separated list of project types they're best suited for",
              "analysis": {
                "strengths": ["str"],
                "risks": ["str"],
                "marketPosition": "str",
                "responsePattern": "str"
              }
            }
            """,
                supplier.getCompanyName(),
                enrichment.getCrawlerFactsJson(),
                enrichment.getLlmSummary(),
                supplier.getGoogleRating(),
                supplier.getGoogleReviewCount(),
                supplier.getTotalRfqsSent(),
                supplier.getTotalBidsReceived(),
                supplier.getAvgResponseTimeHours(),
                supplier.getIsVerified(),
                taxDebtInfo,
                enrichment.getYearsInBusiness() != null ? enrichment.getYearsInBusiness() : "Unknown",
                enrichment.getEmployeeCount() != null ? enrichment.getEmployeeCount() : "Unknown",
                enrichment.getAnnualRevenue() != null ? "EUR " + enrichment.getAnnualRevenue() : "Unknown",
                enrichment.getPublicProcurementCount() != null ? enrichment.getPublicProcurementCount() : "Unknown");

        Optional<String> cached = aiCacheService.getCached(prompt, "tier3-enrichment");
        String response = cached.orElseGet(() -> {
            String r = anthropicService.callClaude(prompt);
            if (r != null) aiCacheService.cache(prompt, "tier3-enrichment", r, 168);
            return r;
        });

        if (response != null) {
            try {
                String json = extractJson(response);
                JsonNode root = objectMapper.readTree(json);
                enrichment.setRiskScore(root.path("riskScore").asInt(50));
                enrichment.setReliabilityScore(root.path("reliabilityScore").asInt(50));
                enrichment.setPriceCompetitiveness(root.path("priceCompetitiveness").asText("MEDIUM"));
                enrichment.setRecommendedFor(root.path("recommendedFor").asText());
                enrichment.setDeepAnalysisJson(json);
            } catch (Exception e) {
                log.error("Error parsing Tier 3 response: {}", e.getMessage());
                enrichment.setDeepAnalysisJson(response);
            }
        }

        enrichment.setTier3CompletedAt(LocalDateTime.now());
        enrichment.setCacheExpiresAt(LocalDateTime.now().plusDays(CACHE_DAYS));
        enrichment = enrichmentRepository.save(enrichment);
        log.info("Tier 3 enrichment completed for supplier: {}", supplier.getCompanyName());
        return enrichment;
    }

    @Scheduled(fixedRate = 86400000) // Daily
    @Transactional
    public void batchEnrich() {
        // Re-enrich expired entries
        List<CompanyEnrichment> expired = enrichmentRepository.findByCacheExpiresAtBefore(LocalDateTime.now());
        for (CompanyEnrichment enrichment : expired) {
            try {
                UUID supplierId = enrichment.getSupplier().getId();
                enrichTier1(supplierId);
                enrichTier2(supplierId);
                enrichTier3(supplierId);
            } catch (Exception e) {
                log.error("Batch enrichment failed for supplier {}: {}", enrichment.getSupplier().getId(), e.getMessage());
            }
        }
        if (!expired.isEmpty()) {
            log.info("Batch enrichment completed for {} suppliers", expired.size());
        }
    }

    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start != -1 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }
}
