package com.buildquote.controller;

import com.buildquote.dto.*;
import com.buildquote.service.RfqService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class RfqController {

    private final RfqService rfqService;

    public RfqController(RfqService rfqService) {
        this.rfqService = rfqService;
    }

    // Create and send RFQ campaign
    @PostMapping("/rfq/send")
    public ResponseEntity<CampaignDto> sendRfq(@RequestBody RfqRequest request) {
        CampaignDto campaign = rfqService.createAndSendCampaign(request);
        return ResponseEntity.ok(campaign);
    }

    // Get all campaigns
    @GetMapping("/campaigns")
    public ResponseEntity<List<CampaignDto>> getCampaigns() {
        return ResponseEntity.ok(rfqService.getAllCampaigns());
    }

    // Get single campaign with bids
    @GetMapping("/campaigns/{id}")
    public ResponseEntity<CampaignDto> getCampaign(@PathVariable String id) {
        return ResponseEntity.ok(rfqService.getCampaign(id));
    }

    // Get bid page info (public, no auth)
    @GetMapping("/bids/page/{token}")
    public ResponseEntity<BidPageDto> getBidPage(@PathVariable String token) {
        try {
            BidPageDto page = rfqService.getBidPage(token);
            return ResponseEntity.ok(page);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Submit bid (public, no auth)
    @PostMapping("/bids/submit/{token}")
    public ResponseEntity<?> submitBid(@PathVariable String token, @RequestBody BidSubmissionRequest request) {
        try {
            BidDto bid = rfqService.submitBid(token, request);
            return ResponseEntity.ok(bid);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Get all bids (for dashboard)
    @GetMapping("/bids")
    public ResponseEntity<List<BidDto>> getAllBids() {
        return ResponseEntity.ok(rfqService.getAllBids());
    }

    // Get campaign bids with AI analysis
    @GetMapping("/rfq/{campaignId}/bids")
    public ResponseEntity<List<BidDto>> getCampaignBidsWithAnalysis(@PathVariable String campaignId) {
        try {
            List<BidDto> bids = rfqService.getCampaignBidsWithAnalysis(campaignId);
            return ResponseEntity.ok(bids);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
