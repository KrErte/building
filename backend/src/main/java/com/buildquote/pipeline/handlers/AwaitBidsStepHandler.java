package com.buildquote.pipeline.handlers;

import com.buildquote.pipeline.PipelineContext;
import com.buildquote.pipeline.StepHandler;
import com.buildquote.pipeline.StepResult;
import com.buildquote.repository.BidRepository;
import com.buildquote.repository.RfqCampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class AwaitBidsStepHandler implements StepHandler {

    private final RfqCampaignRepository campaignRepository;
    private final BidRepository bidRepository;

    @Override
    public String getStepType() {
        return "AWAIT_BIDS";
    }

    @Override
    public StepResult execute(PipelineContext context) {
        @SuppressWarnings("unchecked")
        List<String> campaignIds = context.get("campaignIds", List.class);

        if (campaignIds == null || campaignIds.isEmpty()) {
            return StepResult.success("No campaigns to await bids for");
        }

        int totalBids = 0;
        int campaignsWithBids = 0;

        for (String campaignIdStr : campaignIds) {
            var campaign = campaignRepository.findById(UUID.fromString(campaignIdStr)).orElse(null);
            if (campaign != null) {
                int bidCount = bidRepository.countByCampaign(campaign);
                if (bidCount > 0) {
                    campaignsWithBids++;
                    totalBids += bidCount;
                }
            }
        }

        // If we have bids for at least half the campaigns, proceed
        if (campaignsWithBids >= (campaignIds.size() / 2) || totalBids > 0) {
            context.put("totalBidsReceived", totalBids);
            log.info("Bids received: {} total across {} campaigns", totalBids, campaignsWithBids);
            return StepResult.success(Map.of("totalBids", totalBids, "campaignsWithBids", campaignsWithBids));
        }

        // Still waiting for bids
        return StepResult.awaiting("Waiting for bids: " + totalBids + " received so far");
    }

    @Override
    public boolean canRetry() {
        return false;
    }
}
