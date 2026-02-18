package com.buildquote.controller;

import com.buildquote.dto.ComparisonResultDto;
import com.buildquote.dto.NegotiationDto;
import com.buildquote.service.QuoteComparisonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@Slf4j
public class AnalysisController {

    private final QuoteComparisonService quoteComparisonService;

    @GetMapping("/campaign/{id}/compare")
    public ResponseEntity<ComparisonResultDto> compareBids(@PathVariable UUID id) {
        log.info("Comparing bids for campaign: {}", id);
        ComparisonResultDto result = quoteComparisonService.compareBids(id);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/bid/{id}")
    public ResponseEntity<Map<String, Object>> analyzeBid(@PathVariable UUID id) {
        log.info("Analyzing bid: {}", id);
        Map<String, Object> analysis = quoteComparisonService.analyzeBid(id);
        return ResponseEntity.ok(analysis);
    }

    @GetMapping("/bid/{id}/negotiate")
    public ResponseEntity<NegotiationDto> negotiationStrategy(@PathVariable UUID id) {
        log.info("Generating negotiation strategy for bid: {}", id);
        NegotiationDto strategy = quoteComparisonService.generateNegotiationStrategy(id);
        return ResponseEntity.ok(strategy);
    }
}
