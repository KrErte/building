package com.buildquote.service;

import com.buildquote.entity.CompanyEnrichment;
import com.buildquote.entity.ProjectStage;
import com.buildquote.entity.Supplier;
import com.buildquote.repository.CompanyEnrichmentRepository;
import com.buildquote.repository.SupplierRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SupplierMatchingService {

    private final SupplierRepository supplierRepository;
    private final CompanyEnrichmentRepository enrichmentRepository;
    private final ObjectMapper objectMapper;

    // Scoring weights
    private static final double WEIGHT_CATEGORY = 0.40;
    private static final double WEIGHT_LOCATION = 0.20;
    private static final double WEIGHT_RESPONSE_HISTORY = 0.15;
    private static final double WEIGHT_RISK = 0.15;
    private static final double WEIGHT_FINANCIAL = 0.10;

    private static final double TAX_DEBT_PENALTY = 0.30;
    private static final int MIN_CANDIDATES = 3;
    private static final int MIN_SCORE_THRESHOLD = 30;
    private static final int MAX_RESULTS = 15;

    // Adjacent categories for broadening
    private static final Map<String, List<String>> ADJACENT_CATEGORIES = Map.ofEntries(
            Map.entry("ELECTRICAL", List.of("HVAC", "GENERAL_CONSTRUCTION")),
            Map.entry("PLUMBING", List.of("HVAC", "GENERAL_CONSTRUCTION")),
            Map.entry("HVAC", List.of("PLUMBING", "ELECTRICAL")),
            Map.entry("TILING", List.of("FLOORING", "FINISHING")),
            Map.entry("FINISHING", List.of("TILING", "FLOORING", "FACADE")),
            Map.entry("ROOFING", List.of("FACADE", "GENERAL_CONSTRUCTION")),
            Map.entry("FACADE", List.of("ROOFING", "FINISHING")),
            Map.entry("LANDSCAPING", List.of("GENERAL_CONSTRUCTION")),
            Map.entry("WINDOWS_DOORS", List.of("FACADE", "GENERAL_CONSTRUCTION")),
            Map.entry("FLOORING", List.of("TILING", "FINISHING")),
            Map.entry("DEMOLITION", List.of("GENERAL_CONSTRUCTION")),
            Map.entry("GENERAL_CONSTRUCTION", List.of("FINISHING", "DEMOLITION"))
    );

    @Data
    @Builder
    public static class ScoredSupplier {
        private UUID supplierId;
        private String companyName;
        private String email;
        private int totalScore;
        private int categoryScore;
        private int locationScore;
        private int responseScore;
        private int riskScore;
        private int financialScore;
        private boolean hasTaxDebt;
    }

    public List<ScoredSupplier> findAndScoreSuppliers(ProjectStage stage, String projectLocation) {
        String category = stage.getCategory();
        if (category == null) {
            log.warn("Stage '{}' has no category, cannot match suppliers", stage.getName());
            return List.of();
        }

        // Get candidate suppliers matching the category
        List<Supplier> candidates = supplierRepository.findByCategory(category, 100);

        // Score each candidate
        List<ScoredSupplier> scored = candidates.stream()
                .map(s -> scoreSupplier(s, category, projectLocation))
                .sorted(Comparator.comparingInt(ScoredSupplier::getTotalScore).reversed())
                .collect(Collectors.toList());

        // Filter by minimum threshold
        List<ScoredSupplier> qualified = scored.stream()
                .filter(s -> s.getTotalScore() >= MIN_SCORE_THRESHOLD)
                .collect(Collectors.toList());

        // Broadening: if < MIN_CANDIDATES, remove location filter
        if (qualified.size() < MIN_CANDIDATES) {
            log.info("Only {} qualified suppliers for category {}, broadening (ignoring location)",
                    qualified.size(), category);
            qualified = scored.stream()
                    .map(s -> {
                        // Recalculate without location penalty
                        int adjusted = s.getTotalScore() + (int) ((100 - s.getLocationScore()) * WEIGHT_LOCATION);
                        return ScoredSupplier.builder()
                                .supplierId(s.getSupplierId())
                                .companyName(s.getCompanyName())
                                .email(s.getEmail())
                                .totalScore(Math.min(100, adjusted))
                                .categoryScore(s.getCategoryScore())
                                .locationScore(100) // treat as matching
                                .responseScore(s.getResponseScore())
                                .riskScore(s.getRiskScore())
                                .financialScore(s.getFinancialScore())
                                .hasTaxDebt(s.isHasTaxDebt())
                                .build();
                    })
                    .filter(s -> s.getTotalScore() >= MIN_SCORE_THRESHOLD)
                    .sorted(Comparator.comparingInt(ScoredSupplier::getTotalScore).reversed())
                    .collect(Collectors.toList());
        }

        // Further broadening: include adjacent categories
        if (qualified.size() < MIN_CANDIDATES) {
            List<String> adjacent = ADJACENT_CATEGORIES.getOrDefault(category, List.of());
            for (String adjCategory : adjacent) {
                if (qualified.size() >= MIN_CANDIDATES) break;
                List<Supplier> adjCandidates = supplierRepository.findByCategory(adjCategory, 50);
                for (Supplier s : adjCandidates) {
                    if (qualified.stream().noneMatch(q -> q.getSupplierId().equals(s.getId()))) {
                        ScoredSupplier adjScored = scoreSupplier(s, category, projectLocation);
                        // Reduce category score for adjacent match
                        int adjustedCat = (int) (adjScored.getCategoryScore() * 0.6);
                        int adjustedTotal = (int) (adjustedCat * WEIGHT_CATEGORY
                                + adjScored.getLocationScore() * WEIGHT_LOCATION
                                + adjScored.getResponseScore() * WEIGHT_RESPONSE_HISTORY
                                + adjScored.getRiskScore() * WEIGHT_RISK
                                + adjScored.getFinancialScore() * WEIGHT_FINANCIAL);
                        if (adjScored.isHasTaxDebt()) {
                            adjustedTotal = (int) (adjustedTotal * (1.0 - TAX_DEBT_PENALTY));
                        }
                        qualified.add(ScoredSupplier.builder()
                                .supplierId(s.getId())
                                .companyName(s.getCompanyName())
                                .email(s.getEmail())
                                .totalScore(adjustedTotal)
                                .categoryScore(adjustedCat)
                                .locationScore(adjScored.getLocationScore())
                                .responseScore(adjScored.getResponseScore())
                                .riskScore(adjScored.getRiskScore())
                                .financialScore(adjScored.getFinancialScore())
                                .hasTaxDebt(adjScored.isHasTaxDebt())
                                .build());
                    }
                }
            }
            qualified.sort(Comparator.comparingInt(ScoredSupplier::getTotalScore).reversed());
        }

        List<ScoredSupplier> result = qualified.stream()
                .limit(MAX_RESULTS)
                .collect(Collectors.toList());

        log.info("Matched {} suppliers for stage '{}' (category={})",
                result.size(), stage.getName(), category);
        return result;
    }

    public String toJson(List<ScoredSupplier> suppliers) {
        try {
            return objectMapper.writeValueAsString(suppliers);
        } catch (Exception e) {
            log.error("Failed to serialize scored suppliers: {}", e.getMessage());
            return "[]";
        }
    }

    private ScoredSupplier scoreSupplier(Supplier supplier, String targetCategory, String projectLocation) {
        int categoryScore = scoreCategoryOverlap(supplier, targetCategory);
        int locationScore = scoreLocation(supplier, projectLocation);
        int responseScore = scoreResponseHistory(supplier);
        int riskScore = scoreRisk(supplier);
        int financialScore = scoreFinancialHealth(supplier);

        int rawTotal = (int) (
                categoryScore * WEIGHT_CATEGORY
                + locationScore * WEIGHT_LOCATION
                + responseScore * WEIGHT_RESPONSE_HISTORY
                + riskScore * WEIGHT_RISK
                + financialScore * WEIGHT_FINANCIAL
        );

        // Tax debt penalty
        boolean hasTaxDebt = false;
        Optional<CompanyEnrichment> enrichment = enrichmentRepository.findBySupplierId(supplier.getId());
        if (enrichment.isPresent() && Boolean.TRUE.equals(enrichment.get().getTaxDebt())) {
            hasTaxDebt = true;
            rawTotal = (int) (rawTotal * (1.0 - TAX_DEBT_PENALTY));
        }

        int total = Math.max(0, Math.min(100, rawTotal));

        return ScoredSupplier.builder()
                .supplierId(supplier.getId())
                .companyName(supplier.getCompanyName())
                .email(supplier.getEmail())
                .totalScore(total)
                .categoryScore(categoryScore)
                .locationScore(locationScore)
                .responseScore(responseScore)
                .riskScore(riskScore)
                .financialScore(financialScore)
                .hasTaxDebt(hasTaxDebt)
                .build();
    }

    private int scoreCategoryOverlap(Supplier supplier, String targetCategory) {
        if (supplier.getCategories() == null || supplier.getCategories().length == 0) {
            // Check EMTAK code match
            if (supplier.getEmtakCode() != null && !supplier.getEmtakCode().isBlank()) {
                return 60; // partial match via EMTAK
            }
            return 20; // no category info at all
        }

        for (String cat : supplier.getCategories()) {
            if (targetCategory.equalsIgnoreCase(cat.trim())) {
                return 100; // exact match
            }
        }

        // Check if any of their categories are adjacent
        List<String> adjacent = ADJACENT_CATEGORIES.getOrDefault(targetCategory, List.of());
        for (String cat : supplier.getCategories()) {
            if (adjacent.contains(cat.trim())) {
                return 50; // adjacent category
            }
        }

        return 10; // no match
    }

    private int scoreLocation(Supplier supplier, String projectLocation) {
        if (projectLocation == null || projectLocation.isBlank()) return 50;

        String locLower = projectLocation.toLowerCase();

        // Check city match
        if (supplier.getCity() != null && supplier.getCity().toLowerCase().contains(locLower)) {
            return 100;
        }

        // Check service areas
        if (supplier.getServiceAreas() != null) {
            for (String area : supplier.getServiceAreas()) {
                if (area.toLowerCase().contains(locLower)) {
                    return 90;
                }
            }
        }

        // Check county match (same region)
        if (supplier.getCounty() != null) {
            return 40; // at least in Estonia
        }

        return 20; // no location match
    }

    private int scoreResponseHistory(Supplier supplier) {
        if (supplier.getTotalRfqsSent() == null || supplier.getTotalRfqsSent() == 0) {
            return 50; // neutral - no history
        }

        int sent = supplier.getTotalRfqsSent();
        int received = supplier.getTotalBidsReceived() != null ? supplier.getTotalBidsReceived() : 0;
        double ratio = (double) received / sent;

        if (ratio >= 0.7) return 100;
        if (ratio >= 0.5) return 80;
        if (ratio >= 0.3) return 60;
        if (ratio >= 0.1) return 40;
        return 20; // very low response rate
    }

    private int scoreRisk(Supplier supplier) {
        Optional<CompanyEnrichment> enrichment = enrichmentRepository.findBySupplierId(supplier.getId());
        if (enrichment.isEmpty() || enrichment.get().getRiskScore() == null) {
            return 50; // neutral
        }
        // riskScore in enrichment: 0=safest, 100=highest risk
        // Invert for scoring: low risk = high score
        return 100 - enrichment.get().getRiskScore();
    }

    private int scoreFinancialHealth(Supplier supplier) {
        int score = 50; // baseline

        // Google rating bonus
        if (supplier.getGoogleRating() != null) {
            double rating = supplier.getGoogleRating().doubleValue();
            if (rating >= 4.5) score += 30;
            else if (rating >= 4.0) score += 20;
            else if (rating >= 3.5) score += 10;
            else if (rating < 3.0) score -= 10;
        }

        // Verified bonus
        if (Boolean.TRUE.equals(supplier.getIsVerified())) {
            score += 20;
        }

        return Math.max(0, Math.min(100, score));
    }
}
