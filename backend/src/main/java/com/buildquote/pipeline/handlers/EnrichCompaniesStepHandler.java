package com.buildquote.pipeline.handlers;

import com.buildquote.pipeline.PipelineContext;
import com.buildquote.pipeline.StepHandler;
import com.buildquote.pipeline.StepResult;
import com.buildquote.service.CompanyEnrichmentPipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class EnrichCompaniesStepHandler implements StepHandler {

    private final CompanyEnrichmentPipelineService enrichmentService;

    private static final int MAX_CONCURRENT_ENRICHMENTS = 5;

    @Override
    public String getStepType() {
        return "ENRICH_COMPANIES";
    }

    @Override
    public StepResult execute(PipelineContext context) {
        @SuppressWarnings("unchecked")
        List<String> supplierIds = context.get("matchedSupplierIds", List.class);

        if (supplierIds == null || supplierIds.isEmpty()) {
            log.info("No matched supplier IDs in context, skipping enrichment");
            context.put("enrichmentComplete", true);
            return StepResult.success(Map.of("status", "no_suppliers_to_enrich"));
        }

        // Deduplicate supplier IDs
        Set<String> uniqueIds = new LinkedHashSet<>(supplierIds);
        log.info("Enriching {} unique suppliers (max {} concurrent)", uniqueIds.size(), MAX_CONCURRENT_ENRICHMENTS);

        ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_ENRICHMENTS);
        int enriched = 0;
        int failed = 0;

        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (String idStr : uniqueIds) {
                UUID supplierId = UUID.fromString(idStr);
                futures.add(executor.submit(() -> {
                    try {
                        enrichmentService.enrichTier1(supplierId);
                        enrichmentService.enrichTier2(supplierId);
                        enrichmentService.enrichTier3(supplierId);
                        return true;
                    } catch (Exception e) {
                        log.warn("Enrichment failed for supplier {}: {}", supplierId, e.getMessage());
                        return false;
                    }
                }));
            }

            for (Future<Boolean> future : futures) {
                try {
                    if (future.get(60, TimeUnit.SECONDS)) {
                        enriched++;
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    failed++;
                }
            }
        } finally {
            executor.shutdown();
        }

        context.put("enrichmentComplete", true);
        context.put("suppliersEnriched", enriched);

        log.info("Enrichment completed: {} succeeded, {} failed", enriched, failed);
        return StepResult.success(Map.of("enriched", enriched, "failed", failed));
    }
}
