package com.buildquote.service;

import com.buildquote.entity.*;
import com.buildquote.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailMatchingService {

    private final InboundEmailRepository inboundEmailRepository;
    private final RfqEmailRepository rfqEmailRepository;
    private final RfqCampaignRepository campaignRepository;
    private final BidRepository bidRepository;
    private final QuoteParserService quoteParserService;
    private final MarketPriceLearningService marketPriceLearningService;

    // Pattern to match RFQ tokens in subject or body
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-f0-9]{48}");

    // Pattern to match reference codes like BQ-0042-03
    private static final Pattern REFERENCE_CODE_PATTERN = Pattern.compile("BQ-\\d{4}-\\d{2}");

    @Transactional
    public void matchAndProcess(InboundEmail inboundEmail) {
        // Strategy 1 (highest priority): Match by reference code in subject/body
        RfqCampaign matchedCampaign = matchByReferenceCode(inboundEmail.getSubject(), inboundEmail.getBody());
        RfqEmail matched = null;
        String matchStrategy = null;

        if (matchedCampaign != null) {
            // Find the RFQ email for this sender in this campaign
            matched = findRfqEmailForSender(matchedCampaign, inboundEmail.getFromEmail());
            matchStrategy = "REFERENCE_CODE";
        }

        // Strategy 2: Match by subject token (existing)
        if (matched == null) {
            matched = matchBySubjectToken(inboundEmail.getSubject());
            if (matched != null) matchStrategy = "SUBJECT_TOKEN";
        }

        // Strategy 3 (lowest priority): Match by sender email to most recent campaign
        if (matched == null) {
            matched = matchBySenderEmail(inboundEmail.getFromEmail());
            if (matched != null) matchStrategy = "SENDER_EMAIL";
        }

        if (matched != null) {
            inboundEmail.setMatchedRfqEmail(matched);
            inboundEmail.setMatchedCampaign(matched.getCampaign());
            inboundEmail.setMatchStrategy(matchStrategy);

            // Try to parse bid from email body
            Bid bid = tryParseBid(inboundEmail, matched);
            if (bid != null) {
                inboundEmail.setParsedBid(bid);

                // Trigger market price learning
                try {
                    marketPriceLearningService.onBidReceived(bid);
                } catch (Exception e) {
                    log.warn("Market price learning failed for bid {}: {}", bid.getId(), e.getMessage());
                }
            }

            inboundEmail.setProcessed(true);
            inboundEmail.setProcessedAt(LocalDateTime.now());
            inboundEmailRepository.save(inboundEmail);

            log.info("Matched inbound email from {} to campaign {} via {}",
                    inboundEmail.getFromEmail(),
                    matched.getCampaign().getId(),
                    matchStrategy);
        } else {
            log.info("No match found for inbound email from {}", inboundEmail.getFromEmail());
            inboundEmail.setProcessed(true);
            inboundEmail.setProcessedAt(LocalDateTime.now());
            inboundEmailRepository.save(inboundEmail);
        }
    }

    private RfqCampaign matchByReferenceCode(String subject, String body) {
        String combined = (subject != null ? subject : "") + " " + (body != null ? body : "");
        Matcher matcher = REFERENCE_CODE_PATTERN.matcher(combined);
        while (matcher.find()) {
            String refCode = matcher.group();
            Optional<RfqCampaign> campaign = campaignRepository.findByReferenceCode(refCode);
            if (campaign.isPresent()) {
                return campaign.get();
            }
        }
        return null;
    }

    private RfqEmail findRfqEmailForSender(RfqCampaign campaign, String fromEmail) {
        if (fromEmail == null) return null;
        String email = extractEmail(fromEmail);

        List<RfqEmail> campaignEmails = rfqEmailRepository.findByCampaign(campaign);
        return campaignEmails.stream()
                .filter(e -> email.equalsIgnoreCase(e.getSupplierEmail()))
                .findFirst()
                .orElse(campaignEmails.isEmpty() ? null : campaignEmails.get(0));
    }

    private RfqEmail matchBySubjectToken(String subject) {
        if (subject == null) return null;

        Matcher matcher = TOKEN_PATTERN.matcher(subject);
        while (matcher.find()) {
            String token = matcher.group();
            Optional<RfqEmail> rfqEmail = rfqEmailRepository.findByToken(token);
            if (rfqEmail.isPresent()) {
                return rfqEmail.get();
            }
        }
        return null;
    }

    private RfqEmail matchBySenderEmail(String fromEmail) {
        if (fromEmail == null) return null;
        String email = extractEmail(fromEmail);

        // Find most recent RFQ sent to this email
        List<RfqEmail> allEmails = rfqEmailRepository.findAll();
        return allEmails.stream()
                .filter(e -> email.equalsIgnoreCase(e.getSupplierEmail()))
                .filter(e -> "SENT".equals(e.getStatus()) || "OPENED".equals(e.getStatus()))
                .max(Comparator.comparing(e -> e.getSentAt() != null ? e.getSentAt() : LocalDateTime.MIN))
                .orElse(null);
    }

    private String extractEmail(String fromEmail) {
        return fromEmail.contains("<")
                ? fromEmail.substring(fromEmail.indexOf('<') + 1, fromEmail.indexOf('>'))
                : fromEmail;
    }

    private Bid tryParseBid(InboundEmail inboundEmail, RfqEmail rfqEmail) {
        // Check if bid already exists for this RFQ
        if (bidRepository.findByRfqEmail(rfqEmail).isPresent()) {
            log.info("Bid already exists for RFQ email {}", rfqEmail.getId());
            return null;
        }

        try {
            Map<String, Object> parsed = quoteParserService.parseQuoteFromEmail(inboundEmail.getBody());

            if (parsed.containsKey("error")) {
                log.warn("Could not parse bid from email: {}", parsed.get("error"));
                return null;
            }

            BigDecimal price = (BigDecimal) parsed.get("totalPrice");
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                log.info("No valid price found in email from {}", inboundEmail.getFromEmail());
                return null;
            }

            Bid bid = new Bid();
            bid.setRfqEmail(rfqEmail);
            bid.setCampaign(rfqEmail.getCampaign());
            bid.setSupplierName(rfqEmail.getSupplierName());
            bid.setSupplierEmail(rfqEmail.getSupplierEmail());
            bid.setPrice(price);
            bid.setCurrency((String) parsed.getOrDefault("currency", "EUR"));
            bid.setTimelineDays((Integer) parsed.get("timelineDays"));
            bid.setNotes("Auto-parsed from email: " + inboundEmail.getSubject());
            bid.setStatus("RECEIVED");
            bid.setSource("EMAIL");
            bid.setSubmittedAt(LocalDateTime.now());

            bid = bidRepository.save(bid);

            // Update RFQ email status
            rfqEmail.setStatus("RESPONDED");
            rfqEmail.setRespondedAt(LocalDateTime.now());
            rfqEmailRepository.save(rfqEmail);

            // Update campaign stats
            RfqCampaign campaign = rfqEmail.getCampaign();
            campaign.setTotalResponded(campaign.getTotalResponded() + 1);
            campaignRepository.save(campaign);

            log.info("Auto-parsed bid of EUR {} from {} for campaign {} (source=EMAIL)",
                    price, inboundEmail.getFromEmail(), campaign.getId());
            return bid;

        } catch (Exception e) {
            log.error("Error parsing bid from email: {}", e.getMessage());
            return null;
        }
    }
}
