package com.buildquote.controller;

import com.buildquote.entity.MarketPrice;
import com.buildquote.repository.MarketPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/prices")
@RequiredArgsConstructor
@Slf4j
public class PriceController {

    private final MarketPriceRepository marketPriceRepository;

    @GetMapping("/check")
    public ResponseEntity<?> checkPrice(
            @RequestParam String category,
            @RequestParam(defaultValue = "1") BigDecimal quantity,
            @RequestParam(defaultValue = "Tallinn") String region
    ) {
        log.info("Price check for category: {}, quantity: {}, region: {}", category, quantity, region);

        // Try to find price by category and region first
        Optional<MarketPrice> priceOpt = marketPriceRepository.findByCategoryAndRegion(category, region);

        // Fall back to category only
        if (priceOpt.isEmpty()) {
            priceOpt = marketPriceRepository.findByCategory(category);
        }

        if (priceOpt.isEmpty()) {
            // Return default estimates if no data
            Map<String, Object> defaultEstimate = new HashMap<>();
            defaultEstimate.put("category", category);
            defaultEstimate.put("minPrice", 20);
            defaultEstimate.put("maxPrice", 50);
            defaultEstimate.put("avgPrice", 35);
            defaultEstimate.put("medianPrice", 35);
            defaultEstimate.put("unit", "m²");
            defaultEstimate.put("sampleCount", 0);

            Map<String, Object> estimatedTotal = new HashMap<>();
            estimatedTotal.put("min", quantity.multiply(BigDecimal.valueOf(20)).setScale(0, RoundingMode.HALF_UP));
            estimatedTotal.put("max", quantity.multiply(BigDecimal.valueOf(50)).setScale(0, RoundingMode.HALF_UP));
            estimatedTotal.put("avg", quantity.multiply(BigDecimal.valueOf(35)).setScale(0, RoundingMode.HALF_UP));
            defaultEstimate.put("estimatedTotal", estimatedTotal);

            return ResponseEntity.ok(defaultEstimate);
        }

        MarketPrice price = priceOpt.get();

        // Apply region multiplier if different region
        BigDecimal multiplier = price.getRegionMultiplier() != null ? price.getRegionMultiplier() : BigDecimal.ONE;

        BigDecimal minPrice = price.getMinPrice().multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
        BigDecimal maxPrice = price.getMaxPrice().multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
        BigDecimal avgPrice = price.getAvgPrice().multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
        BigDecimal medianPrice = price.getMedianPrice() != null ?
                price.getMedianPrice().multiply(multiplier).setScale(2, RoundingMode.HALF_UP) : avgPrice;

        Map<String, Object> response = new HashMap<>();
        response.put("category", category);
        response.put("minPrice", minPrice);
        response.put("maxPrice", maxPrice);
        response.put("avgPrice", avgPrice);
        response.put("medianPrice", medianPrice);
        response.put("unit", price.getUnit());
        response.put("sampleCount", price.getSampleCount());

        // Calculate estimated totals
        Map<String, Object> estimatedTotal = new HashMap<>();
        estimatedTotal.put("min", quantity.multiply(minPrice).setScale(0, RoundingMode.HALF_UP));
        estimatedTotal.put("max", quantity.multiply(maxPrice).setScale(0, RoundingMode.HALF_UP));
        estimatedTotal.put("avg", quantity.multiply(avgPrice).setScale(0, RoundingMode.HALF_UP));
        response.put("estimatedTotal", estimatedTotal);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/categories")
    public ResponseEntity<?> getCategories() {
        return ResponseEntity.ok(marketPriceRepository.findAll());
    }
}
