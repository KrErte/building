package com.buildquote.controller;

import com.buildquote.dto.PriceBreakdownDTO;
import com.buildquote.dto.SupplierPriceDTO;
import com.buildquote.service.PriceBreakdownService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class PriceBreakdownController {

    private final PriceBreakdownService priceBreakdownService;

    /**
     * Get detailed price breakdown for a project stage
     */
    @GetMapping("/projects/{projectId}/stages/{stageId}/price-breakdown")
    public ResponseEntity<PriceBreakdownDTO> getPriceBreakdown(
            @PathVariable String projectId,
            @PathVariable String stageId,
            @RequestParam String category,
            @RequestParam BigDecimal quantity,
            @RequestParam(defaultValue = "m2") String unit
    ) {
        log.info("Getting price breakdown for project={}, stage={}, category={}, quantity={}, unit={}",
                projectId, stageId, category, quantity, unit);

        PriceBreakdownDTO breakdown = priceBreakdownService.calculateBreakdown(category, quantity, unit);
        return ResponseEntity.ok(breakdown);
    }

    /**
     * Get price breakdown by category (without project context)
     */
    @GetMapping("/prices/breakdown")
    public ResponseEntity<PriceBreakdownDTO> getPriceBreakdownDirect(
            @RequestParam String category,
            @RequestParam BigDecimal quantity,
            @RequestParam(defaultValue = "m2") String unit
    ) {
        log.info("Getting direct price breakdown for category={}, quantity={}, unit={}", category, quantity, unit);

        PriceBreakdownDTO breakdown = priceBreakdownService.calculateBreakdown(category, quantity, unit);
        return ResponseEntity.ok(breakdown);
    }

    /**
     * Get supplier price comparison for a specific material
     */
    @GetMapping("/materials/{materialName}/suppliers")
    public ResponseEntity<List<SupplierPriceDTO>> getSupplierPrices(
            @PathVariable String materialName,
            @RequestParam(defaultValue = "estonia") String region
    ) {
        log.info("Getting supplier prices for material={}, region={}", materialName, region);

        List<SupplierPriceDTO> prices = priceBreakdownService.getSupplierPrices(materialName, region);
        return ResponseEntity.ok(prices);
    }
}
