package com.buildquote.pipeline.handlers;

import com.buildquote.pipeline.PipelineContext;
import com.buildquote.pipeline.StepHandler;
import com.buildquote.pipeline.StepResult;
import com.buildquote.service.QuoteComparisonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class CompareBidsStepHandler implements StepHandler {

    private final QuoteComparisonService quoteComparisonService;

    @Override
    public String getStepType() {
        return "COMPARE_BIDS";
    }

    @Override
    public StepResult execute(PipelineContext context) {
        @SuppressWarnings("unchecked")
        List<String> campaignIds = context.get("campaignIds", List.class);

        if (campaignIds == null || campaignIds.isEmpty()) {
            return StepResult.success("No campaigns to compare");
        }

        int compared = 0;
        for (String campaignIdStr : campaignIds) {
            try {
                UUID campaignId = UUID.fromString(campaignIdStr);
                var result = quoteComparisonService.compareBids(campaignId);
                if (result.getTotalBids() > 0) {
                    compared++;
                    log.info("Compared {} bids for campaign {}: best value = {}",
                            result.getTotalBids(), campaignId, result.getBestValue());
                }
            } catch (Exception e) {
                log.warn("Failed to compare bids for campaign {}: {}", campaignIdStr, e.getMessage());
            }
        }

        context.put("comparisonComplete", true);
        context.put("campaignsCompared", compared);

        log.info("Bid comparison completed: {}/{} campaigns compared", compared, campaignIds.size());
        return StepResult.success(Map.of("campaignsCompared", compared, "totalCampaigns", campaignIds.size()));
    }
}
