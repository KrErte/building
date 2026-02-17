package com.buildquote.controller;

import com.buildquote.service.EstimatePriceService;
import com.buildquote.service.EstimatePriceService.EstimateResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/prices")
@RequiredArgsConstructor
public class PriceEstimateController {

    private final EstimatePriceService estimatePriceService;

    @PostMapping("/estimate")
    public ResponseEntity<EstimateResult> calculateEstimate(@RequestBody Map<String, String> request) {
        String description = request.get("description");
        if (description == null || description.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        EstimateResult result = estimatePriceService.calculateEstimate(description);
        return ResponseEntity.ok(result);
    }
}
