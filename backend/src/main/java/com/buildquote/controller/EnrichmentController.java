package com.buildquote.controller;

import com.buildquote.dto.CompanyEnrichmentDto;
import com.buildquote.entity.CompanyEnrichment;
import com.buildquote.repository.CompanyEnrichmentRepository;
import com.buildquote.service.CompanyEnrichmentPipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/enrichment")
@RequiredArgsConstructor
@Slf4j
public class EnrichmentController {

    private final CompanyEnrichmentPipelineService enrichmentService;
    private final CompanyEnrichmentRepository enrichmentRepository;

    @GetMapping("/supplier/{id}")
    public ResponseEntity<?> getEnrichment(@PathVariable UUID id) {
        return enrichmentRepository.findBySupplierId(id)
                .map(e -> ResponseEntity.ok(CompanyEnrichmentDto.fromEntity(e)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/supplier/{id}/enrich")
    public ResponseEntity<?> enrichSupplier(@PathVariable UUID id) {
        log.info("Starting full enrichment for supplier: {}", id);
        try {
            enrichmentService.enrichTier1(id);
            enrichmentService.enrichTier2(id);
            enrichmentService.enrichTier3(id);

            CompanyEnrichment enrichment = enrichmentRepository.findBySupplierId(id)
                    .orElseThrow();
            return ResponseEntity.ok(CompanyEnrichmentDto.fromEntity(enrichment));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/supplier/{id}/risk")
    public ResponseEntity<?> getRiskAssessment(@PathVariable UUID id) {
        return enrichmentRepository.findBySupplierId(id)
                .map(e -> ResponseEntity.ok(Map.of(
                        "supplierId", id,
                        "riskScore", e.getRiskScore() != null ? e.getRiskScore() : -1,
                        "reliabilityScore", e.getReliabilityScore() != null ? e.getReliabilityScore() : -1,
                        "priceCompetitiveness", e.getPriceCompetitiveness() != null ? e.getPriceCompetitiveness() : "UNKNOWN",
                        "tier3Complete", e.getTier3CompletedAt() != null
                )))
                .orElse(ResponseEntity.notFound().build());
    }
}
