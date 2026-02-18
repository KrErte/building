package com.buildquote.service;

import com.buildquote.dto.ComparisonResultDto;
import com.buildquote.dto.NegotiationDto;
import com.buildquote.entity.*;
import com.buildquote.repository.*;
import com.buildquote.repository.CompanyEnrichmentRepository;
import com.buildquote.repository.SupplierRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class QuoteComparisonService {

    private final BidRepository bidRepository;
    private final RfqCampaignRepository campaignRepository;
    private final BidAnalysisRepository bidAnalysisRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final CompanyEnrichmentRepository enrichmentRepository;
    private final SupplierRepository supplierRepository;
    private final AnthropicService anthropicService;
    private final AiCacheService aiCacheService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ComparisonResultDto compareBids(UUID campaignId) {
        RfqCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("Campaign not found"));

        List<Bid> bids = bidRepository.findByCampaignOrderBySubmittedAtDesc(campaign);
        if (bids.isEmpty()) {
            return ComparisonResultDto.builder()
                    .campaignId(campaignId)
                    .totalBids(0)
                    .recommendation("No bids received yet")
                    .build();
        }

        // Build comparison prompt
        StringBuilder prompt = new StringBuilder();
        prompt.append("Compare these construction bids for \"").append(campaign.getTitle()).append("\":\n");
        prompt.append("Category: ").append(campaign.getCategory()).append("\n");
        prompt.append("Location: ").append(campaign.getLocation()).append("\n");
        if (campaign.getMaxBudget() != null) {
            prompt.append("Budget: €").append(campaign.getMaxBudget()).append("\n");
        }
        prompt.append("\nBids:\n");

        for (int i = 0; i < bids.size(); i++) {
            Bid bid = bids.get(i);
            prompt.append(i + 1).append(". ").append(bid.getSupplierName())
                    .append(": EUR ").append(bid.getPrice());
            if (bid.getTimelineDays() != null) {
                prompt.append(", ").append(bid.getTimelineDays()).append(" days");
            }
            if (bid.getNotes() != null) {
                prompt.append(", notes: ").append(bid.getNotes());
            }

            // Include enrichment/risk data per bidder
            appendEnrichmentData(prompt, bid);
            prompt.append("\n");
        }

        prompt.append("""

            Provide analysis as JSON:
            {
              "rankings": [{"supplierName": "str", "rank": 1, "score": 85, "reason": "str"}],
              "bestValue": "supplier name",
              "riskFlags": [{"supplierName": "str", "flag": "str"}],
              "recommendation": "detailed recommendation text",
              "priceSpread": {"min": 0, "max": 0, "median": 0}
            }
            """);

        String promptStr = prompt.toString();
        Optional<String> cached = aiCacheService.getCached(promptStr, "comparison");
        String response;

        if (cached.isPresent()) {
            response = cached.get();
        } else {
            response = anthropicService.callClaude(promptStr);
            if (response != null) {
                aiCacheService.cache(promptStr, "comparison", response, 4);
            }
        }

        ComparisonResultDto result = parseComparisonResponse(response, campaignId, bids);

        // Store analysis
        BidAnalysis analysis = BidAnalysis.builder()
                .campaign(campaign)
                .analysisType(BidAnalysis.AnalysisType.COMPARISON)
                .analysisJson(response)
                .summary(result.getRecommendation())
                .confidenceScore(new BigDecimal("0.85"))
                .build();
        bidAnalysisRepository.save(analysis);

        return result;
    }

    @Transactional
    public Map<String, Object> analyzeBid(UUID bidId) {
        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(() -> new RuntimeException("Bid not found"));

        RfqCampaign campaign = bid.getCampaign();

        String prompt = String.format("""
            Deep analysis of a construction bid:
            Supplier: %s
            Price: €%s
            Category: %s
            Location: %s
            Timeline: %s days
            Notes: %s

            Provide detailed analysis as JSON:
            {
              "priceAssessment": "str - fair/overpriced/underpriced with reasoning",
              "riskLevel": "LOW/MEDIUM/HIGH",
              "riskFactors": ["str"],
              "strengths": ["str"],
              "weaknesses": ["str"],
              "overallScore": 0-100,
              "recommendation": "str"
            }
            """, bid.getSupplierName(), bid.getPrice(), campaign.getCategory(),
                campaign.getLocation(), bid.getTimelineDays(), bid.getNotes());

        Optional<String> cached = aiCacheService.getCached(prompt, "bid-analysis");
        String response = cached.orElseGet(() -> {
            String r = anthropicService.callClaude(prompt);
            if (r != null) aiCacheService.cache(prompt, "bid-analysis", r, 6);
            return r;
        });

        Map<String, Object> result = new HashMap<>();
        result.put("bidId", bidId);
        result.put("supplierName", bid.getSupplierName());
        result.put("price", bid.getPrice());

        if (response != null) {
            try {
                String json = extractJson(response);
                JsonNode root = objectMapper.readTree(json);
                result.put("priceAssessment", root.path("priceAssessment").asText());
                result.put("riskLevel", root.path("riskLevel").asText());
                result.put("overallScore", root.path("overallScore").asInt());
                result.put("recommendation", root.path("recommendation").asText());

                List<String> riskFactors = new ArrayList<>();
                root.path("riskFactors").forEach(n -> riskFactors.add(n.asText()));
                result.put("riskFactors", riskFactors);

                List<String> strengths = new ArrayList<>();
                root.path("strengths").forEach(n -> strengths.add(n.asText()));
                result.put("strengths", strengths);

                List<String> weaknesses = new ArrayList<>();
                root.path("weaknesses").forEach(n -> weaknesses.add(n.asText()));
                result.put("weaknesses", weaknesses);
            } catch (Exception e) {
                log.error("Error parsing bid analysis: {}", e.getMessage());
                result.put("analysis", response);
            }
        }

        // Store analysis
        BidAnalysis analysis = BidAnalysis.builder()
                .bid(bid)
                .campaign(campaign)
                .analysisType(BidAnalysis.AnalysisType.PRICE_CHECK)
                .analysisJson(response)
                .summary((String) result.get("recommendation"))
                .build();
        bidAnalysisRepository.save(analysis);

        return result;
    }

    @Transactional
    public NegotiationDto generateNegotiationStrategy(UUID bidId) {
        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(() -> new RuntimeException("Bid not found"));

        RfqCampaign campaign = bid.getCampaign();

        // Get market price for reference
        Optional<MarketPrice> marketPrice = campaign.getCategory() != null
                ? marketPriceRepository.findByCategory(campaign.getCategory())
                : Optional.empty();

        BigDecimal marketMedian = marketPrice.map(MarketPrice::getMedianPrice).orElse(null);

        // Get other bids for leverage
        List<Bid> otherBids = bidRepository.findByCampaignOrderBySubmittedAtDesc(campaign).stream()
                .filter(b -> !b.getId().equals(bidId))
                .collect(Collectors.toList());

        BigDecimal lowestOther = otherBids.stream()
                .map(Bid::getPrice)
                .min(BigDecimal::compareTo)
                .orElse(null);

        String prompt = String.format("""
            Generate a negotiation strategy for this construction bid:
            Supplier: %s, Price: €%s, Category: %s
            Market median price/unit: %s
            Lowest competing bid: %s
            Number of competing bids: %d

            Provide strategy as JSON:
            {
              "targetPrice": number,
              "discountPercent": number,
              "leveragePoints": ["str"],
              "counterOfferReasoning": "str",
              "negotiationTone": "FRIENDLY/FIRM/AGGRESSIVE",
              "suggestedMessage": "str - professional negotiation email text"
            }
            """, bid.getSupplierName(), bid.getPrice(), campaign.getCategory(),
                marketMedian != null ? "€" + marketMedian : "unknown",
                lowestOther != null ? "€" + lowestOther : "none",
                otherBids.size());

        Optional<String> cached = aiCacheService.getCached(prompt, "negotiation");
        String response = cached.orElseGet(() -> {
            String r = anthropicService.callClaude(prompt);
            if (r != null) aiCacheService.cache(prompt, "negotiation", r, 2);
            return r;
        });

        NegotiationDto result = NegotiationDto.builder()
                .bidId(bidId)
                .supplierName(bid.getSupplierName())
                .currentPrice(bid.getPrice())
                .build();

        if (response != null) {
            try {
                String json = extractJson(response);
                JsonNode root = objectMapper.readTree(json);
                result.setTargetPrice(root.has("targetPrice") ? new BigDecimal(root.get("targetPrice").asText()) : null);
                result.setDiscountPercent(root.has("discountPercent") ? new BigDecimal(root.get("discountPercent").asText()) : null);
                result.setCounterOfferReasoning(root.path("counterOfferReasoning").asText());
                result.setNegotiationTone(root.path("negotiationTone").asText("FRIENDLY"));
                result.setSuggestedMessage(root.path("suggestedMessage").asText());

                List<String> leveragePoints = new ArrayList<>();
                root.path("leveragePoints").forEach(n -> leveragePoints.add(n.asText()));
                result.setLeveragePoints(leveragePoints);
            } catch (Exception e) {
                log.error("Error parsing negotiation response: {}", e.getMessage());
                result.setCounterOfferReasoning(response);
            }
        }

        // Store analysis
        BidAnalysis analysis = BidAnalysis.builder()
                .bid(bid)
                .campaign(campaign)
                .analysisType(BidAnalysis.AnalysisType.NEGOTIATION)
                .analysisJson(response)
                .summary(result.getCounterOfferReasoning())
                .build();
        bidAnalysisRepository.save(analysis);

        return result;
    }

    private ComparisonResultDto parseComparisonResponse(String response, UUID campaignId, List<Bid> bids) {
        ComparisonResultDto.ComparisonResultDtoBuilder builder = ComparisonResultDto.builder()
                .campaignId(campaignId)
                .totalBids(bids.size());

        // Calculate price spread
        List<BigDecimal> prices = bids.stream().map(Bid::getPrice).sorted().collect(Collectors.toList());
        builder.minPrice(prices.get(0));
        builder.maxPrice(prices.get(prices.size() - 1));
        if (!prices.isEmpty()) {
            builder.medianPrice(prices.get(prices.size() / 2));
        }

        if (response != null) {
            try {
                String json = extractJson(response);
                JsonNode root = objectMapper.readTree(json);
                builder.recommendation(root.path("recommendation").asText());
                builder.bestValue(root.path("bestValue").asText());

                List<ComparisonResultDto.BidRanking> rankings = new ArrayList<>();
                if (root.has("rankings") && root.get("rankings").isArray()) {
                    for (JsonNode r : root.get("rankings")) {
                        rankings.add(ComparisonResultDto.BidRanking.builder()
                                .supplierName(r.path("supplierName").asText())
                                .rank(r.path("rank").asInt())
                                .score(r.path("score").asInt())
                                .reason(r.path("reason").asText())
                                .build());
                    }
                }
                builder.rankings(rankings);

                List<ComparisonResultDto.RiskFlag> riskFlags = new ArrayList<>();
                if (root.has("riskFlags") && root.get("riskFlags").isArray()) {
                    for (JsonNode f : root.get("riskFlags")) {
                        riskFlags.add(ComparisonResultDto.RiskFlag.builder()
                                .supplierName(f.path("supplierName").asText())
                                .flag(f.path("flag").asText())
                                .build());
                    }
                }
                builder.riskFlags(riskFlags);
            } catch (Exception e) {
                log.error("Error parsing comparison response: {}", e.getMessage());
                builder.recommendation(response);
            }
        }

        return builder.build();
    }

    private void appendEnrichmentData(StringBuilder prompt, Bid bid) {
        if (bid.getRfqEmail() == null || bid.getRfqEmail().getSupplierId() == null) return;

        try {
            var enrichment = enrichmentRepository.findBySupplierId(bid.getRfqEmail().getSupplierId());
            if (enrichment.isPresent()) {
                CompanyEnrichment e = enrichment.get();
                prompt.append("\n     Risk data: ");
                if (Boolean.TRUE.equals(e.getTaxDebt())) {
                    prompt.append("TAX DEBT (EUR ").append(e.getTaxDebtAmount()).append("), ");
                }
                if (e.getRiskScore() != null) {
                    prompt.append("riskScore=").append(e.getRiskScore()).append(", ");
                }
                if (e.getYearsInBusiness() != null) {
                    prompt.append("yearsInBusiness=").append(e.getYearsInBusiness()).append(", ");
                }
                if (e.getPublicProcurementCount() != null && e.getPublicProcurementCount() > 0) {
                    prompt.append("publicProcurementWins=").append(e.getPublicProcurementCount()).append(", ");
                }
                if (e.getReliabilityScore() != null) {
                    prompt.append("reliability=").append(e.getReliabilityScore());
                }
            }
        } catch (Exception ex) {
            // Ignore enrichment lookup errors
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
