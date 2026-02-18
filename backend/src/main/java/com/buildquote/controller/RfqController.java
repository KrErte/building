package com.buildquote.controller;

import com.buildquote.dto.*;
import com.buildquote.entity.User;
import com.buildquote.repository.UserRepository;
import com.buildquote.security.UserPrincipal;
import com.buildquote.service.RfqService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RfqController {

    private final RfqService rfqService;
    private final UserRepository userRepository;

    // Create and send RFQ campaign
    @PostMapping("/rfq/send")
    public ResponseEntity<CampaignDto> sendRfq(@RequestBody RfqRequest request,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        User user = getUser(principal);
        CampaignDto campaign = rfqService.createAndSendCampaign(request);
        return ResponseEntity.ok(campaign);
    }

    // Get all campaigns for current user
    @GetMapping("/campaigns")
    public ResponseEntity<List<CampaignDto>> getCampaigns(@AuthenticationPrincipal UserPrincipal principal) {
        User user = getUser(principal);
        return ResponseEntity.ok(rfqService.getAllCampaigns());
    }

    // Get single campaign with bids
    @GetMapping("/campaigns/{id}")
    public ResponseEntity<CampaignDto> getCampaign(@PathVariable String id,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        User user = getUser(principal);
        return ResponseEntity.ok(rfqService.getCampaign(id));
    }

    // Get bid page info (public, no auth - token-based)
    @GetMapping("/bids/page/{token}")
    public ResponseEntity<BidPageDto> getBidPage(@PathVariable String token) {
        try {
            BidPageDto page = rfqService.getBidPage(token);
            return ResponseEntity.ok(page);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Submit bid (public, no auth - token-based)
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
    public ResponseEntity<List<BidDto>> getAllBids(@AuthenticationPrincipal UserPrincipal principal) {
        User user = getUser(principal);
        return ResponseEntity.ok(rfqService.getAllBids());
    }

    // Get campaign bids with AI analysis
    @GetMapping("/rfq/{campaignId}/bids")
    public ResponseEntity<List<BidDto>> getCampaignBidsWithAnalysis(@PathVariable String campaignId,
                                                                     @AuthenticationPrincipal UserPrincipal principal) {
        User user = getUser(principal);
        try {
            List<BidDto> bids = rfqService.getCampaignBidsWithAnalysis(campaignId);
            return ResponseEntity.ok(bids);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    private User getUser(UserPrincipal principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
